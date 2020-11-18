package io.realm.gradle

import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

class RealmCompilerSubplugin : KotlinCompilerPluginSupportPlugin {

    companion object {
        // TODO POSTPONED Consider embedding these from the build.gradle's pluginVersion task just
        //  as with the version. But leave it for now as they should be quite stable.
        // Modules has to match ${project.group}:${project.name} to make composite build work
        const val groupId = "io.realm.kotlin"
        const val artifactId = "plugin-compiler"
        const val artifactIdShadeSuffix = "-shaded"
        const val version = PLUGIN_VERSION
        // The id used for passing compiler options from command line
        const val compilerPluginId = "io.realm.kotlin"
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean {
        return kotlinCompilation.target.project.plugins.findPlugin(RealmCompilerSubplugin::class.java) != null
    }

    override fun getCompilerPluginId(): String {
        return compilerPluginId
    }

    override fun getPluginArtifact(): SubpluginArtifact {
        // Modules has to match ${project.group}:${project.name} to make composite build work
        return SubpluginArtifact(groupId, artifactId, version)
    }

    override fun getPluginArtifactForNative(): SubpluginArtifact {
        // Modules has to match ${project.group}:${project.name} to make composite build work
        return SubpluginArtifact(groupId, artifactId + artifactIdShadeSuffix, version)
    }

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        return project.provider {
            emptyList<SubpluginOption>()
        }
    }
}
