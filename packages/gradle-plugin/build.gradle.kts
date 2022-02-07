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
import kotlin.text.toBoolean

plugins {
    kotlin("jvm")
    kotlin("kapt")
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version Versions.gradlePluginPublishPlugin
    id("realm-publisher")
}

repositories {
    google()
}

dependencies {
    compileOnly(kotlin("gradle-plugin"))
    compileOnly("com.android.tools.build:gradle:${Versions.Android.buildTools}")
    // JAX-B dependencies for JDK 9+ (this is not available in JVM env 'java.lang.NoClassDefFoundError: javax/xml/bind/DatatypeConverter'
    // and it was removed in Java 11 https://stackoverflow.com/a/43574427
    implementation("javax.xml.bind:jaxb-api:2.3.1")
}

val mavenPublicationName = "gradlePlugin"

fun createMarkerArtifact(): Boolean {
    val value = properties.getOrDefault("generatePluginArtifactMarker", "false") as String
    return value.toBoolean()
}

pluginBundle {
    website = "https://github.com/realm/realm-kotlin"
    vcsUrl = "https://github.com/realm/realm-kotlin"
    tags = listOf("MongoDB", "Realm", "Database", "Kotlin", "Mobile", "Multiplatform", "Android", "KMM")

    mavenCoordinates {
        groupId = Realm.group
        artifactId = Realm.gradlePluginId
        version = Realm.version
    }
}

gradlePlugin {
    plugins {
        create("RealmPlugin") {
            id = Realm.pluginPortalId
            displayName = "Realm Kotlin Plugin"
            description = "Gradle plugin for the Realm Kotlin SDK, supporting Android and Multiplatform. " +
                "Realm is a mobile database: Build better apps faster."
            implementationClass = "io.realm.gradle.RealmPlugin"
        }
        isAutomatedPublishing = createMarkerArtifact()
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
            from(components["java"])
        }
    }
}

java {
    withSourcesJar()
    withJavadocJar()
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<JavaCompile> {
    sourceCompatibility = Versions.jvmTarget
    targetCompatibility = Versions.jvmTarget
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
