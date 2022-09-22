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

package io.realm.kotlin

import Realm
import io.github.gradlenexus.publishplugin.NexusPublishExtension
import io.github.gradlenexus.publishplugin.NexusPublishPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.net.URI
import java.time.Duration

// Custom options for POM configurations that might differ between Realm modules
open class PomOptions {
    open var name: String = ""
    open var description: String = ""
}

// Configure how the Realm module is published
open class RealmPublishExtensions {
    open var pom: PomOptions = PomOptions()
    open fun pom(action: Action<PomOptions>) {
        action.execute(pom)
    }
}

fun getPropertyValue(project: Project, propertyName: String, defaultValue: String = ""): String {
    if (project.hasProperty(propertyName)) {
        return project.property(propertyName) as String
    }
    val systemValue: String? = System.getenv(propertyName)
    return systemValue ?: defaultValue
}

fun hasProperty(project: Project, propertyName: String): Boolean {
    val systemProp: String? = System.getenv(propertyName)
    val projectProp: Boolean = project.hasProperty(propertyName)
    return projectProp || (systemProp != null && systemProp.isNotEmpty())
}

// Plugin responsible for handling publishing to mavenLocal and Maven Central.
class RealmPublishPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // Configure constants required by the publishing process
        val signBuild: Boolean = hasProperty(project,"signBuild")
        configureSignedBuild(signBuild, this)
    }

    private fun configureTestRepository(project: Project) {
        val testRepository = getPropertyValue(project, "testRepository")
        if (!testRepository.isEmpty()) {
            project.extensions.getByType<PublishingExtension>().apply {
                repositories {
                    maven {
                        name = "Test"
                        url =
                            URI("file://${project.rootProject.rootDir.absolutePath}/$testRepository")
                    }
                }
            }
        }
    }

    private fun configureSignedBuild(signBuild: Boolean, project: Project) {
        // The nexus publisher plugin can only be applied to top-level projects.
        // See https://github.com/gradle-nexus/publish-plugin/issues/81
        // Also, we should not apply the MavenPublish plugin to the root project as it will result in an
        // empty `realm-kotlin` artifact being deployed to Maven Central.
        val isRootProject: Boolean = (project == project.rootProject)
        if (isRootProject) {
            configureRootProject(project)
        } else {
            configureSubProject(project, signBuild)
            configureTestRepository(project)
        }
    }

    private fun configureSubProject(project: Project, signBuild: Boolean) {
        // ID for the Realm Kotlin PGP key file.
        val keyId = "1F48C9B0"
        // Apparently Gradle treats properties define through a gradle.properties file differently
        // than those defined through the commandline using `-P`. This is a problem with new
        // line characters as found in an ascii-armoured PGP file. To ensure we can work around this,
        // all newlines have been replaced with `#` and thus needs to be reverted here.
        val ringFile: String = getPropertyValue(project,"signSecretRingFileKotlin").replace('#', '\n')
        val password: String = getPropertyValue(project, "signPasswordKotlin")

        with(project) {
            plugins.apply(SigningPlugin::class.java)
            plugins.apply(MavenPublishPlugin::class.java)

            // Create the RealmPublish plugin. It must evaluate after all other plugins as it modifies their output.
            // Only allow configuration from sub projects as the top-level project is just a placeholder
            extensions.create<RealmPublishExtensions>("realmPublish")

            afterEvaluate {
                project.extensions.findByType<RealmPublishExtensions>()?.run {
                    configurePom(project, pom)
                }
            }

            // Configure signing of artifacts
            extensions.getByType<SigningExtension>().apply {
                isRequired = signBuild
                useInMemoryPgpKeys(keyId, ringFile, password)
                sign(project.extensions.getByType<PublishingExtension>().publications)
            }
        }
    }

    private fun configureRootProject(project: Project) {
        val sonatypeStagingProfileId = "78c19333e4450f"

        with(project) {
            project.plugins.apply(NexusPublishPlugin::class.java)

            // Configure upload to Maven Central.
            // The nexus publisher plugin can only be applied to top-level projects.
            // See https://github.com/gradle-nexus/publish-plugin/issues/81
            extensions.getByType<NexusPublishExtension>().apply {
                this.packageGroup.set("io.realm.kotlin")
                this.repositories {
                    sonatype {
                        this.stagingProfileId.set(sonatypeStagingProfileId)
                        this.username.set(getPropertyValue(project,"ossrhUsername"))
                        this.password.set(getPropertyValue(project,"ossrhPassword"))
                    }
                }
                this.transitionCheckOptions {
                    maxRetries.set(720) // Retry for 2 hours. Sometimes Maven Central is really slow!
                    delayBetween.set(Duration.ofSeconds(10))
                }
            }
        }
    }

    private fun configurePom(project: Project, options: PomOptions) {
        project.extensions.getByType<PublishingExtension>().apply {
            publications.withType<MavenPublication>().all {
                pom {
                    name.set(options.name)
                    description.set(options.description)
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
                    developers {
                        developers {
                            developer {
                                name.set(Realm.Developer.name)
                                email.set(Realm.Developer.email)
                                organization.set(Realm.Developer.organization)
                                organizationUrl.set(Realm.Developer.organizationUrl)
                            }
                        }
                    }
                }
            }
        }
    }
}
