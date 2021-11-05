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

object Realm {
    val ciBuild = (System.getenv("JENKINS_HOME") != null)
    const val version = "0.8.0-SNAPSHOT"
    const val group = "io.realm.kotlin"
    const val projectUrl = "https://realm.io"
    const val pluginPortalId = "io.realm.kotlin"
    // Modules has to match ${project.group}:${project.name} to make composite build work
    const val compilerPluginId = "plugin-compiler"
    const val compilerPluginIdNative = "plugin-compiler-shaded"
    const val cInteropId = "cinterop"
    const val jniSwigStubsId = "jni-swig-stub"
    const val gradlePluginId = "gradle-plugin"

    object License {
        const val name = "The Apache License, Version 2.0"
        const val url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
        const val distribution = "repo"
    }
    object IssueManagement {
        const val system = "Github"
        const val url = "https://github.com/realm/realm-kotlin/issues"
    }
    object SCM {
        const val connection = "scm:git:git://github.com/realm/realm-kotlin.git"
        const val developerConnection = "scm:git:ssh://github.com/realm/realm-kotlin.git"
        const val url = "https://github.com/realm/realm-kotlin"
    }
    object Developer {
        const val name = "Realm"
        const val email = "info@realm.io"
        const val organization = "MongoDB"
        const val organizationUrl = "https://www.mongodb.com"
    }
}

object Versions {
    object Android {
        const val minSdk = 16
        const val targetSdk = 31
        const val compileSdkVersion = 31
        const val buildToolsVersion = "31.0.0"
        const val buildTools = "7.1.0-beta02" // https://maven.google.com/web/index.html?q=gradle#com.android.tools.build:gradle
        const val ndkVersion = "23.1.7779620"
    }
    const val androidxStartup = "1.1.0-beta01" // https://maven.google.com/web/index.html?q=startup#androidx.startup:startup-runtime
    const val androidxJunit = "1.1.3-beta02" // https://maven.google.com/web/index.html#androidx.test.ext:junit
    const val androidxTest = "1.4.0-beta02" // https://maven.google.com/web/index.html#androidx.test:rules
    // Must be built with same (major.minor!?) kotlin version as 'kotlin' variable below, to be binary compatible with kotlin
    const val atomicfu = "0.16.3" // https://github.com/Kotlin/kotlinx.atomicfu
    const val autoService = "1.0" // https://mvnrepository.com/artifact/com.google.auto.service/auto-service
    // Not currently used, so mostly here for documentation. Core requires minimum 3.15, but 3.18.1 is available through the Android SDK.
    // Build also tested successfully with 3.21.4 (latest release).
    const val cmake = "3.18.1"
    const val coroutines = "1.5.2-native-mt" // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    const val detektPlugin = "1.17.1" // https://github.com/detekt/detekt
    const val dokka = "1.5.30" // https://github.com/Kotlin/dokka
    const val gradlePluginPublishPlugin = "0.15.0" // https://plugins.gradle.org/plugin/com.gradle.plugin-publish
    const val junit = "4.13.2" // https://mvnrepository.com/artifact/junit/junit
    const val jvmTarget = "1.8"
    const val kotlin = "1.5.31" // https://github.com/JetBrains/kotlin
    const val kotlinCompileTesting = "1.4.2" // https://github.com/tschuchortdev/kotlin-compile-testing
    const val ktlintPlugin = "10.1.0" // https://github.com/jlleitschuh/ktlint-gradle
    const val ktlintVersion = "0.41.0" // https://github.com/pinterest/ktlint
    const val ktor = "1.6.4" // https://kotlinlang.org/docs/releases.html#release-details
    const val nexusPublishPlugin = "1.1.0" // https://github.com/gradle-nexus/publish-plugin
    const val serialization = "1.3.0-RC" // https://kotlinlang.org/docs/releases.html#release-details
    const val shadowJar =  "6.1.0" // https://mvnrepository.com/artifact/com.github.johnrengelman.shadow/com.github.johnrengelman.shadow.gradle.plugin?repo=gradle-plugins
    const val multidex = "2.0.1" // https://developer.android.com/jetpack/androidx/releases/multidex
}

// Could be actual Dependency objects
object Deps {
    const val autoService = "com.google.auto.service:auto-service:${Versions.autoService}"
    const val autoServiceAnnotation = "com.google.auto.service:auto-service-annotations:${Versions.autoService}"
}
