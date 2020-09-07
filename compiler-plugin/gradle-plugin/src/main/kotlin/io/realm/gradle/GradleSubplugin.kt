package io.realm.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class GradleSubplugin: KotlinCompilerPluginSupportPlugin {

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.findPlugin(GradleSubplugin::class.java) != null
    }

    override fun getCompilerPluginId(): String {
        return "realm-compiler-plugin"
    }

    override fun getPluginArtifact(): SubpluginArtifact =
            SubpluginArtifact("io.realm", "compiler-plugin", "0.0.1-SNAPSHOT")

    override fun getPluginArtifactForNative(): SubpluginArtifact =
        SubpluginArtifact("io.realm", "compiler-plugin-shaded", "0.0.1-SNAPSHOT")

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            emptyList<SubpluginOption>()
        }
    }
}
