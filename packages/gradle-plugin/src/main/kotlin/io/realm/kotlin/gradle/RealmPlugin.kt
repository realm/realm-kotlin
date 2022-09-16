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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.build.event.BuildEventsListenerRegistry
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import javax.inject.Inject

@Suppress("unused")
open class RealmPlugin : Plugin<Project> {

    private val logger: Logger = Logging.getLogger("realm-plugin")

    @Inject
    public open fun getBuildEventsRegistry(): BuildEventsListenerRegistry { TODO("Should have been replaced by Gradle.") }

    override fun apply(project: Project) {
        project.pluginManager.apply(RealmCompilerSubplugin::class.java)

        // Run analytics as a Build Service to support Gradle Configuration Cache
        val serviceProvider: Provider<AnalyticsService> = project.gradle.sharedServices.registerIfAbsent(
            "realm-analytics",
            AnalyticsService::class.java
        ) { /* Do nothing */ }
        getBuildEventsRegistry().onTaskCompletion(serviceProvider)

        // Stand alone Android projects have not initialized kotlin plugin when applying this, so
        // postpone dependency injection till after evaluation.
        project.afterEvaluate {
            val kotlin = project.extensions.findByName("kotlin")
            // TODO AUTO-SETUP To ease configuration we could/should inject dependencies to our
            //  library, but await better insight into when/what to inject and supply appropriate
            //  opt-out options through our own extension?
            //  Dependencies should probably be added by source set and not by target, as
            //  kotlin.sourceSets.getByName("commonMain").dependencies (or "main" for Android), but
            // FIXME This seems to throw an error during build. Any reason we are setting it?
//            when (kotlin) {
//                 is KotlinSingleTargetExtension -> {
//                     updateKotlinOption(kotlin.target)
//                 }
//                 is KotlinMultiplatformExtension -> {
//                     kotlin.targets.all { target -> updateKotlinOption(target) }
//                 }
//                 TODO AUTO-SETUP Should we report errors? Probably an oversighted case
//                 else ->
//                    TODO("Cannot 'realm-kotlin' library dependency to ${kotlin::class.qualifiedName}")
//            }

            // Create the analytics during configuration because it needs access to the project
            // in order to gather project relevant information in afterEvaluate. Currently
            // there doesn't seem a way to get this information during the Execution Phase.
            @Suppress("TooGenericExceptionCaught")
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
