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
    const val version = "0.0.1-SNAPSHOT"
    const val group = "io.realm.kotlin"
    const val projectUrl = "https://realm.io"
    const val plugin = "realm-kotlin"
    const val pluginId = "$plugin"
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
}

object Versions {
    object Android {
        const val minSdk = 16
        const val targetSdk = 29
        const val compileSdkVersion = 29
        const val buildToolsVersion = "29.0.2"
        const val buildTools = "4.1.0"
        const val ndkVersion = "22.0.6917172"
    }
    const val androidxJunit = "1.1.2" // https://maven.google.com/web/index.html#androidx.test.ext:junit
    const val androidxTest = "1.3.0" // https://maven.google.com/web/index.html#androidx.test:rules
    const val autoService = "1.0-rc6"
    const val coroutines = "1.4.2"
    const val detektPlugin = "1.16.0" // https://github.com/detekt/detekt
    const val dokka = "1.4.20" // https://github.com/Kotlin/dokka
    const val junit = "4.12"
    const val jvmTarget = "1.8"
    const val kotlin = "1.4.30"
    const val kotlinCompileTesting = "1.2.6" // https://github.com/tschuchortdev/kotlin-compile-testing
    const val ktlintPlugin = "9.4.1" // https://github.com/jlleitschuh/ktlint-gradle
    const val ktlintVersion = "0.39.0" // https://github.com/pinterest/ktlint
    const val shadowJar =  "5.2.0"
}

// Could be actual Dependency objects
object Deps {
    const val autoService = "com.google.auto.service:auto-service:${Versions.autoService}"
    const val autoServiceAnnotation = "com.google.auto.service:auto-service-annotations:${Versions.autoService}"
}
