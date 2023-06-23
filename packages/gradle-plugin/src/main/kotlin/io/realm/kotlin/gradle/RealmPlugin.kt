/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.gradle

import io.realm.kotlin.gradle.analytics.AnalyticsService
import io.realm.kotlin.gradle.analytics.hexStringify
import io.realm.kotlin.gradle.analytics.sha256Hash
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import javax.inject.Inject

@Suppress("unused")
open class RealmPlugin : Plugin<Project> {

    private val logger: Logger = Logging.getLogger("realm-plugin")

    internal lateinit var anonymizedBundleId: String

    @Inject
    public open fun getBuildEventsRegistry(): BuildEventsListenerRegistry { TODO("Should have been replaced by Gradle.") }

    override fun apply(project: Project) {
        project.pluginManager.apply(RealmCompilerSubplugin::class.java)

        // We build the anonymized bundle id here and pass it to the compiler plugin to ensure
        // that the metrics and sync connection parameters are aligned.
        val bundleId = project.rootProject.name + ":" + project.name
        anonymizedBundleId = hexStringify(sha256Hash(bundleId.toByteArray()))

        // Run analytics as a Build Service to support Gradle Configuration Cache
        val serviceProvider: Provider<AnalyticsService> = project.gradle.sharedServices.registerIfAbsent(
            "realm-analytics",
            AnalyticsService::class.java
        ) { /* Do nothing */ }
        getBuildEventsRegistry().onTaskCompletion(serviceProvider)

        project.configurations.all { conf: Configuration ->
            // Ensure that android unit tests uses the Realm JVM variant rather than Android.
            // This is a bit britle. See https://github.com/realm/realm-kotlin/issues/1404 for
            // a potential improvement.
            if (conf.name.endsWith("UnitTestRuntimeClasspath")) {
                conf.resolutionStrategy.dependencySubstitution { ds: DependencySubstitutions ->
                    with(ds) {
                        substitute(module("io.realm.kotlin:library-base:$PLUGIN_VERSION")).using(
                            module("io.realm.kotlin:library-base-jvm:$PLUGIN_VERSION")
                        )
                        substitute(module("io.realm.kotlin:cinterop:$PLUGIN_VERSION")).using(
                            module("io.realm.kotlin:cinterop-jvm:$PLUGIN_VERSION")
                        )
                    }
                }
            }
        }

        // Stand alone Android projects have not initialized kotlin plugin when applying this, so
        // postpone dependency injection till after evaluation.
        project.afterEvaluate {
            val kotlin: Any? = project.extensions.findByName("kotlin")
            // TODO AUTO-SETUP To ease configuration we could/should inject dependencies to our
            //  library, but await better insight into when/what to inject and supply appropriate
            //  opt-out options through our own extension?
            //  Dependencies should probably be added by source set and not by target, as
            //  kotlin.sourceSets.getByName("commonMain").dependencies (or "main" for Android), but
            when (kotlin) {
                is KotlinSingleTargetExtension<*> -> {
                    updateKotlinOption(kotlin.target)
                }
                is KotlinMultiplatformExtension -> {
                    kotlin.targets.all { target -> updateKotlinOption(target) }
                }
                else -> {
                    // TODO AUTO-SETUP Should we report errors? Probably an oversighted case
                    // TODO("Cannot 'realm-kotlin' library dependency to ${if (kotlin != null) kotlin::class.qualifiedName else "null"}")
                }
            }

            // Create the analytics during configuration because it needs access to the project
            // in order to gather project relevant information in afterEvaluate. Currently
            // there doesn't seem a way to get this information during the Execution Phase.
            @Suppress("SwallowedException", "TooGenericExceptionCaught")
            try {
                val analyticsService: AnalyticsService = serviceProvider.get()
                analyticsService.collectAnalyticsData(it)
            } catch (ex: Exception) {
                // Work-around for https://github.com/gradle/gradle/issues/18821
                // Since this only happens in multi-module projects, this should be fine as
                // the build will still be registered by the first module that starts the service.
            }
        }
    }

    private fun updateKotlinOption(target: KotlinTarget) {
        target.compilations.all { compilation ->
            // Setup correct compiler options
            // FIXME AUTO-SETUP Are these to dangerous to apply under the hood?
            when (val options = compilation.kotlinOptions) {
                is KotlinJvmOptions -> {
                    options.jvmTarget = "1.8"
                }
            }
        }
    }
}
