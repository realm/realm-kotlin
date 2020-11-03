import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    `java`
    `maven-publish`
    id("com.github.johnrengelman.shadow") version Versions.shadowJar
    id("com.jfrog.artifactory")
}

dependencies {
    implementation(project(":plugin-compiler"))
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveClassifier.set("")
        this.destinationDirectory.set(file("$buildDir/libs"))
        relocate("org.jetbrains.kotlin.com.intellij", "com.intellij")
        dependencies {
            exclude {
                it.moduleName != "plugin-compiler"
            }
        }
    }
}
tasks {
    named("jar") {
        actions.clear()
        dependsOn(
            shadowJar
        )
    }
}

publishing {
    publications {
        register<MavenPublication>("compilerPluginShaded") {
            project.shadow.component(this)
            artifactId = Realm.compilerPluginIdNative
            pom {
                name.set("Shaded Compiler Plugin")
                description.set("Shaded compiler plugin for native platforms for Realm Kotlin. This artifact is not " +
                        "supposed to be consumed directly, but through " +
                        "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead.")
                url.set(Realm.projectUrl)
                licenses {
                    license {
                        name.set(Realm.License.name)
                        url.set(Realm.License.url)
                    }
                }
                issueManagement {
                    name.set(Realm.IssueManagement.name)
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

artifactory {
    setContextUrl("https://oss.jfrog.org/artifactory")
    publish(delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
        repository(delegateClosureOf<groovy.lang.GroovyObject> {
            setProperty("repoKey", "oss-snapshot-local")
            setProperty("username", if (project.hasProperty("bintrayUser")) project.properties["bintrayUser"] else "noUser")
            setProperty("password", if (project.hasProperty("bintrayKey")) project.properties["bintrayKey"] else "noKey")
        })
        defaults(delegateClosureOf<groovy.lang.GroovyObject> {
            invokeMethod("publications", "compilerPluginShaded")
        })
    })
}
