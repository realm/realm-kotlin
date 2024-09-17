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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.DependencySubstitutions
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging

@Suppress("unused")
open class RealmPlugin : Plugin<Project> {

    private val logger: Logger = Logging.getLogger("realm-plugin")

    override fun apply(project: Project) {
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
    }
}
