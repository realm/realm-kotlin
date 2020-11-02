plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    `maven-publish`
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
}

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
        register<MavenPublication>("gradlePlugin") {
            artifactId = Realm.compilerPluginIdNative
            pom {
                name.set("Gradle Plugin")
                artifactId = "plugin-gradle"
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
