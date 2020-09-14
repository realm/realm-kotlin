package io.realm.build

import org.gradle.api.Plugin
import org.gradle.api.Project

class Config : Plugin<Project> {

    companion object {
        val VERSION = "0.0.1-SNAPSHOT"
        val GROUP   = "io.realm"

        val COMPILER_PLUGIN = "io.realm.compiler-plugin"
    }

    override fun apply(target: Project) { }

}
