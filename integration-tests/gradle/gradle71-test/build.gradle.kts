/*
 * Copyright 2022 Realm Inc.
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

// Explicitly adding the plugin to the classpath as it makes it easier to control the version
// centrally (don't need version in the 'plugins' block). Further, snapshots are not published with
// marker interface so would need to be added to the classpath manually anyway.
buildscript {
        extra["realmVersion"] = file("${rootProject.rootDir.absolutePath}/../../../buildSrc/src/main/kotlin/Config.kt")
            .readLines()
            .first { it.contains("const val version") }
            .let {
                it.substringAfter("\"").substringBefore("\"")
            }

        repositories {
            maven(url = "file://${rootProject.rootDir.absolutePath}/../../../packages/build/m2-buildrepo")
            gradlePluginPortal()
            google()
            mavenCentral()
        }
        dependencies {
            classpath("com.android.tools.build:gradle:7.0.0")
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.8.0")
            classpath("io.realm.kotlin:gradle-plugin:${rootProject.extra["realmVersion"]}")
        }
}
group = "io.realm.test"
version = rootProject.extra["realmVersion"]

// Attempt to make an easy entry point for verifying all modules. Maybe we could do a better split
// when migrating to GHA.
tasks.register("integrationTest") {
    dependsOn(":single-platform:connectedDebugAndroidTest")
    dependsOn(":multi-platform:cleanAllTests")
    dependsOn(":multi-platform:jvmTest")
    dependsOn(":multi-platform:nativeTest")
}
