plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    `maven-publish`
    id("com.jfrog.artifactory")
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
}

val mavenPublicationName = "gradlePlugin"

gradlePlugin {
    plugins {
        create("RealmPlugin") {
            id = Realm.pluginId
            displayName = "Realm compiler plugin"
            implementationClass = "io.realm.gradle.RealmPlugin"
        }
    }
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.compilerPluginIdNative
            pom {
                name.set("Gradle Plugin")
                artifactId = Realm.gradlePluginId
                from(components["java"])
                description.set("Gradle plugin for Realm Kotlin. Realm is a mobile database: Build better apps faster.")
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

artifactory {
    setContextUrl("https://oss.jfrog.org/artifactory")
    publish(
        delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
            repository(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    setProperty("repoKey", "oss-snapshot-local")
                    setProperty("username", if (project.hasProperty("bintrayUser")) project.properties["bintrayUser"] else "noUser")
                    setProperty("password", if (project.hasProperty("bintrayKey")) project.properties["bintrayKey"] else "noKey")
                }
            )
            defaults(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    invokeMethod("publications", mavenPublicationName)
                }
            )
        }
    )
}

// Make version information available at runtime
val versionDirectory = "$buildDir/generated/source/version/"
sourceSets {
    main {
        java.srcDir(versionDirectory)
    }
}
tasks.create("pluginVersion") {
    val outputDir = file(versionDirectory)

    inputs.property("version", project.version)
    outputs.dir(outputDir)

    doLast {
        val versionFile = file("$outputDir/io/realm/gradle/version.kt")
        versionFile.parentFile.mkdirs()
        versionFile.writeText(
            """
            // Generated file. Do not edit!
            package io.realm.gradle
            internal const val PLUGIN_VERSION = "${project.version}"
            """.trimIndent()
        )
    }
}
tasks.getByName("compileKotlin").dependsOn("pluginVersion")
