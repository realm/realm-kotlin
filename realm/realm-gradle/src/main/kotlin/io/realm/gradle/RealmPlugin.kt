package io.realm.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

@Suppress("unused")
class RealmPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.pluginManager.apply(RealmCompilerSubplugin::class.java)

        // FIXME Need to differentiate on kotlin plugin variants; for now assuming MPP
        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
            target.afterEvaluate {
                kotlin.targets.all { target ->
                    target.compilations.all { compilation ->
                        // Inject library dependency
                        compilation.dependencies {
                            implementation("io.realm:realm-library:${io.realm.gradle.PLUGIN_VERSION}")
                        }
                        // Setup correct compiler options
                        // FIXME Are these to dangerous to apply under the hood?
                        val options = compilation.kotlinOptions
                        when (options) {
                            is KotlinJvmOptions -> {
                                options.jvmTarget = "1.8"
                                options.useIR = true
                            }
                        }
                    }
                }
            }
        }
    }

}
