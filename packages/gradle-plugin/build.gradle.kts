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

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    id("realm-publisher")
}

repositories {
    google()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    compileOnly("com.android.tools.build:gradle:${Versions.Android.buildTools}")
}

val mavenPublicationName = "gradlePlugin"

gradlePlugin {
    plugins {
        create("RealmPlugin") {
            id = Realm.pluginId
            displayName = "Realm compiler plugin"
            implementationClass = "io.realm.gradle.RealmPlugin"
        }
        // FIXME Disable publishing of marker artifact as it is currently causing authentication
        //  issues when uploading to http://oss.jfrog.org/oss-snapshot-local.
        //  NOTE This also disables publication of the marker artifact when publishing to local
        //  maven repository
        //  https://github.com/realm/realm-kotlin/issues/100
        isAutomatedPublishing = false
    }
}

realmPublish {
    pom {
        name = "Gradle Plugin"
        description = "Gradle plugin for Realm Kotlin. Realm is a mobile database: Build better apps faster."
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

java {
    withSourcesJar()
    withJavadocJar()
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
