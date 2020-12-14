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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.delegateClosureOf
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.getPluginByName
import org.gradle.kotlin.dsl.withType
import org.jfrog.gradle.plugin.artifactory.ArtifactoryPlugin
import org.jfrog.gradle.plugin.artifactory.dsl.ArtifactoryPluginConvention
import java.net.URL

// Custom options for POM configurations that might differ between Realm modules
open class PomOptions {
    open var isEnabled: Boolean = true
    open var name: String = ""
    open var description = ""
}

// Custom options for Artifactory configurations that might differ between Realm modules
open class ArtifactoryOptions {
    open var isEnabled: Boolean = true
    open var publications: Array<String> = arrayOf()
}

// Configure how the Realm module is published
open class RealmPublishExtensions {
    open var pom: PomOptions = PomOptions()
    open fun pom(action: Action<PomOptions>) {
        action.execute(pom)
    }

    // The artifactory plugin does some weird things with the `artifactory` keyword, so it seems
    // reservered somehow.
    open var ojo: ArtifactoryOptions = ArtifactoryOptions()
    open fun ojo(action: Action<ArtifactoryOptions>) {
        action.execute(ojo)
    }
}

// Plugin responsible for handling publishing to mavenLocal, OJO and Bintray.
class RealmPublishPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        plugins.apply(MavenPublishPlugin::class.java)
        plugins.apply(ArtifactoryPlugin::class.java)
        extensions.create<RealmPublishExtensions>("realmPublish")
        afterEvaluate {
            project.extensions.findByType<RealmPublishExtensions>()?.run {
                if (pom.isEnabled) {
                    configurePom(project, pom)
                }
                if (ojo.isEnabled) {
                    configureArtifactory(project, ojo)
                }
            }
        }
    }

    private fun configurePom(project: Project, options: PomOptions) {
        project.extensions.getByType<PublishingExtension>().apply {
            publications.withType<MavenPublication>().all {
                pom {
                    if (options.name.isNotEmpty()) {
                        name.set("C Interop")
                    }
                    if (options.description.isNotEmpty()) {
                        description.set(
                                "Wrapper for interacting with Realm Kotlin native code. This artifact is not " +
                                        "supposed to be consumed directly, but through " +
                                        "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
                        )
                    }
                    url.set(Realm.projectUrl)
                    licenses {
                        license {
                            name.set(Realm.License.name)
                            url.set(Realm.License.url)
                        }
                    }
                    issueManagement {
                        system.set(Realm.IssueManagement.system)
                        url.set(Realm.IssueManagement.url)
                    }
                    scm {
                        connection.set(Realm.SCM.connection)
                        developerConnection.set(Realm.SCM.developerConnection)
                        url.set(Realm.SCM.url)
                    }
                }
            }
        }
    }

    private fun configureArtifactory(project: Project, options: ArtifactoryOptions) {
        project.extensions.getByType<PublishingExtension>().apply {
            repositories.maven {
                name = "ojo"
                url = URL("https://oss.jfrog.org/artifactory/oss-snapshot-local").toURI()
                credentials {
                    username = if (System.getProperties().containsKey("bintrayUser")) System.getProperty("bintrayUser") else "noUser"
                    password = if (System.getProperties().containsKey("bintrayKey")) System.getProperty("bintrayKey") else "noKey"
                }
            }
        }
    }
}
