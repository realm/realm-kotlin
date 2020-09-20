package io.realm.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class RealmCompilerSubplugin: KotlinCompilerPluginSupportPlugin {

    companion object {
        // TODO Find a way to align with gradles Config.* properties
        val compilerPluginId = "io.realm-compiler-plugin"
        val groupId = "io.realm"
        val artifactId = "compiler-plugin"
        val artifactIdShadeSuffix = "-shaded"
        val version = PLUGIN_VERSION
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.findPlugin(RealmCompilerSubplugin::class.java) != null
    }

    override fun getCompilerPluginId(): String {
        return compilerPluginId
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        return SubpluginArtifact(groupId, artifactId, version)
    }

    override fun getPluginArtifactForNative(): SubpluginArtifact {
        return SubpluginArtifact(groupId, artifactId + artifactIdShadeSuffix, version)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            emptyList<SubpluginOption>()
        }
    }
}
