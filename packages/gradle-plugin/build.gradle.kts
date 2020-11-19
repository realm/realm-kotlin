plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    id("realm-publisher")
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

realmPublish {
    pom {
        name = "Gradle Plugin"
        description = "Gradle plugin for Realm Kotlin. Realm is a mobile database: Build better apps faster."
    }
    ojo {
        publications = arrayOf(mavenPublicationName)
    }
}

publishing {
    publications {
        register<MavenPublication>(mavenPublicationName) {
            artifactId = Realm.gradlePluginId
            pom {
                artifactId = Realm.gradlePluginId
                from(components["java"])
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
