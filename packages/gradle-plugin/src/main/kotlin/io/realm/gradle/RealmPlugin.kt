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

package io.realm.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@Suppress("unused")
class RealmPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(RealmCompilerSubplugin::class.java)

        project.gradle.addListener(RealmAnalytics())

        // Stand alone Android projects have not initialized kotlin plugin when applying this, so
        // postpone dependency injection till after evaluation
        project.afterEvaluate {
            val kotlin = project.extensions.findByName("kotlin")
            // TODO AUTO-SETUP To ease configuration we could/should inject dependencies to our
            //  library, but await better insight into when/what to inject and supply appropriate
            //  opt-out options through our own extension?
            //  Dependencies should probably be added by source set and not by target, as
            //  kotlin.sourceSets.getByName("commonMain").dependencies (or "main" for Android), but
            when (kotlin) {
                is KotlinSingleTargetExtension -> {
                    updateKotlinOption(kotlin.target)
                }
                is KotlinMultiplatformExtension -> {
                    kotlin.targets.all { target -> updateKotlinOption(target) }
                }
                // TODO AUTO-SETUP Should we report errors? Probably an oversighted case
                // else ->
                //    TODO("Cannot 'realm-kotlin' library dependency to ${kotlin::class.qualifiedName}")
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
