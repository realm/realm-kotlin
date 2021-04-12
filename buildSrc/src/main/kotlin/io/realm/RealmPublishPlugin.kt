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

import Realm
import io.codearte.gradle.nexus.NexusStagingExtension
import io.codearte.gradle.nexus.NexusStagingPlugin
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.signing.SigningExtension
import org.gradle.plugins.signing.SigningPlugin
import java.net.URI

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

fun getPropertyValue(project: Project, propertyName: String): String {
    if (project.hasProperty(propertyName)) {
        return project.property(propertyName) as String
    }
    return System.getenv(propertyName) ?: throw IllegalStateException("Cannot find property: $propertyName")
}

fun hasProperty(project: Project, propertyName: String): Boolean {
    val systemProp: String? = System.getenv(propertyName)
    val projectProp: Boolean = project.hasProperty(propertyName)
    return projectProp || (systemProp != null && systemProp.isNotEmpty())
}

// Plugin responsible for handling publishing to mavenLocal and Maven Central.
class RealmPublishPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = project.run {
        // See https://github.com/Codearte/gradle-nexus-staging-plugin/issues/47#issuecomment-491474045 for further details.
        project.extra["gnsp.disableApplyOnlyOnRootProjectEnforcement"] = "true"

        // Configure constants required by the publishing process
        val signBuild: Boolean = hasProperty(project,"signBuild")
        val keyId = "1F48C9B0"
        // Apparently Gradle treats properties define through a gradle.properties file differently
        // than those defined through the commandline using `-P`. This is a problem with new
        // line characters as found in an ascii-armoured PGP file. To ensure work around this, all newlines
        // have been replaced with `#` and thus needs to be reverted here.
        val ringFile: String = getPropertyValue(project,"signSecretRingFileKotlin").replace('#', '\n')
        val password: String = getPropertyValue(project, "signPassword")
        val sonatypeStagingProfileId = "78c19333e4450f"

        // Apply and configure plugins required to release artifacts locally and to Maven Central
        plugins.apply(MavenPublishPlugin::class.java)
        plugins.apply(SigningPlugin::class.java)
        plugins.apply(NexusStagingPlugin::class.java)

        // Create the RealmPublish plugin. It must evaluate after all other plugins as it modifies their output
        extensions.create<RealmPublishExtensions>("realmPublish")
        afterEvaluate {
            project.extensions.findByType<RealmPublishExtensions>()?.run {
                configurePom(project, pom)
                configureRepository(project)
            }
        }

        // Configure signing of artifacts
        project.extensions.getByType<SigningExtension>().apply {
            isRequired = signBuild
            useInMemoryPgpKeys(keyId, ringFile, password)
            sign(project.extensions.getByType<PublishingExtension>().publications)
        }

        // Configure upload to Maven Central
        project.extensions.getByType<NexusStagingExtension>().apply {
            this.packageGroup = "io.realm"
            this.stagingProfileId = sonatypeStagingProfileId
            this.username = getPropertyValue(project,"ossrhUsername")
            this.password = getPropertyValue(project,"ossrhPassword")
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

    private fun configureRepository(project: Project) {
        project.extensions.getByType<PublishingExtension>().apply {
            val snapshotUri = URI("https://oss.sonatype.org/content/repositories/snapshots/")
            val releaseUri = URI("https://oss.sonatype.org/service/local/staging/deploy/maven2/")
            val isSnapshotRelease = project.version.toString().endsWith("-SNAPSHOT")
            repositories.maven {
                name = "MavenCentral"
                url = if (isSnapshotRelease) snapshotUri else releaseUri
                credentials {
                    username = getPropertyValue(project,"ossrhUsername")
                    password = getPropertyValue(project,"ossrhPassword")
                }
            }
        }
    }
}
