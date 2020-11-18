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
                    options.useIR = true
                }
            }
        }
    }
}
