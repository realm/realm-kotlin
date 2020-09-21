package io.realm.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

class RealmPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply(RealmCompilerSubplugin::class.java)

        // FIXME Need to differentiate on kotlin plugin variants; for now assuming MPP
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.all { target ->
                target.compilations.all { compilation ->
                    compilation.dependencies {
                        implementation("io.realm:realm-library")
                    }
                }

            }
        }
    }

}
