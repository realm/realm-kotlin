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
import org.gradle.api.JavaVersion

/**
 * Enum describing operating systems we can build on.
 *
 * We need to track this in order to control which Kotlin Multiplatform tasks we can safely
 * create on the given Host OS.
 */
enum class OperatingSystem {
    LINUX,
    MACOS_ARM64,
    MACOS_X64,
    WINDOWS;

    fun isWindows(): Boolean {
        return this == WINDOWS
    }

    fun isMacOs(): Boolean{
        return this == MACOS_X64 || this == MACOS_ARM64
    }
}

private fun findHostOs(): OperatingSystem {
    val hostOs = System.getProperty("os.name")
    return if (hostOs.contains("windows", ignoreCase = true)) {
        OperatingSystem.WINDOWS
    } else if (hostOs.contains("linux", ignoreCase = true)) {
        OperatingSystem.LINUX
    } else {
        // Assume MacOS by default
        when(val osArch = System.getProperty("os.arch")) {
            "aarch64" -> OperatingSystem.MACOS_ARM64
            "x86_64" -> OperatingSystem.MACOS_X64
            else -> {
                throw IllegalStateException("Unknown architecture: $osArch")
            }
        }
    }
}

/**
 * Define which Host OS the build is running on.
 */
val HOST_OS: OperatingSystem = findHostOs()

object Realm {
    val ciBuild = (System.getenv("CI") != null)
    const val version = "3.1.0"
    // The version of the native realm binaries to avoid rebuilding them
    const val nativeRealmVersion = "3.0.0"
    const val group = "com.simprints.realm.kotlin"
    const val projectUrl = "https://realm.io"
    const val pluginPortalId = "com.simprints.realm.kotlin"
    // Modules has to match ${project.group}:${project.name} to make composite build work
    const val compilerPluginId = "plugin-compiler"
    const val compilerPluginIdNative = "plugin-compiler-shaded"
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
        const val minSdk = 23
        const val targetSdk = 35
        const val compileSdkVersion = 35
        const val buildTools = "8.12.0" // https://maven.google.com/web/index.html?q=gradle#com.android.tools.build:gradle

    }
    const val androidxJunit = "1.3.0" // https://maven.google.com/web/index.html#androidx.test.ext:junit
    const val androidxTest = "1.7.0" // https://maven.google.com/web/index.html#androidx.test:rules
    // Must be built with same (major.minor!?) kotlin version as 'kotlin' variable below, to be binary compatible with kotlin
    const val atomicfuPlugin = "0.29.0" // https://github.com/Kotlin/kotlinx.atomicfu
    const val autoService = "1.0" // https://mvnrepository.com/artifact/com.google.auto.service/auto-service
    const val coroutines = "1.10.2" // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-core
    const val datetime = "0.4.0" // https://github.com/Kotlin/kotlinx-datetime
    const val detektPlugin = "1.23.6" // https://github.com/detekt/detekt
    const val dokka = "1.9.0" // https://github.com/Kotlin/dokka
    const val gradlePluginPublishPlugin = "0.15.0" // https://plugins.gradle.org/plugin/com.gradle.plugin-publish
    const val junit = "4.13.2" // https://mvnrepository.com/artifact/junit/junit
    const val kbson = "0.4.0" // https://github.com/mongodb/kbson
    // When updating the Kotlin version, also remember to update /examples/min-android-sample/build.gradle.kts
    const val kotlin = "2.2.0" // https://github.com/JetBrains/kotlin and https://kotlinlang.org/docs/releases.html#release-details
    const val kotlinJvmTarget = "17" // Which JVM bytecode version is kotlin compiled to.
    const val kotlinCompileTesting = "0.8.0" // https://github.com/zacsweers/kotlin-compile-testing
    const val ktlint = "0.45.2" // https://github.com/pinterest/ktlint
    const val nexusPublishPlugin = "1.3.0" // https://github.com/gradle-nexus/publish-plugin
    const val okio = "3.16.0" // https://square.github.io/okio/#releases
    const val serializationJson = "1.9.0" // https://kotlinlang.org/docs/releases.html#release-details
    const val shadowJar =  "8.1.1" // https://mvnrepository.com/artifact/com.github.johnrengelman.shadow/com.github.johnrengelman.shadow.gradle.plugin?repo=gradle-plugins
    val sourceCompatibilityVersion = JavaVersion.VERSION_17 // Language level of any Java source code.
    val targetCompatibilityVersion = JavaVersion.VERSION_17 // Version of generated JVM bytecode from Java files.
}

// Could be actual Dependency objects
object Deps {
    const val autoService = "com.google.auto.service:auto-service:${Versions.autoService}"
    const val autoServiceAnnotation = "com.google.auto.service:auto-service-annotations:${Versions.autoService}"
}
