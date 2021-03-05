/*
 * Copyright 2020 JetBrains s.r.o.
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

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("kotlin-android-extensions")
    // TODO Publish marker artifact to OJO to allow applying plugin by id
    //  https://github.com/realm/realm-kotlin/issues/100
    // Apply Realm Kotlin plugin
    // id("realm-kotlin") version Realm.version
    // Apply Realm specific linting plugin to get common Realm linting tasks
    id("realm-lint")
}
// TODO Publish marker artifact to OJO to allow applying plugin by id instead of this
//  https://github.com/realm/realm-kotlin/issues/100
buildscript {
    repositories {
        maven(url = "http://oss.jfrog.org/artifactory/oss-snapshot-local")
    }
    dependencies {
        classpath("io.realm.kotlin:gradle-plugin:${Realm.version}")
    }
}
apply(plugin = "realm-kotlin")

group = "io.realm.example"
version = Realm.version

repositories {
    gradlePluginPortal()
    google()
    jcenter()
    mavenCentral()
    maven(url = "http://oss.jfrog.org/artifactory/oss-snapshot-local")
}
kotlin {
    android()
    // TODO Realm is not available for non-X64 hosts yet
    //  https://github.com/realm/realm-kotlin/issues/72
    ios() {
        binaries {
            framework {
                baseName = "shared"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                // TODO AUTO-SETUP
                implementation("io.realm.kotlin:library:${Realm.version}")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.material:material:1.2.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.12")
            }
        }
        val iosMain by getting
        val iosTest by getting
    }
}
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
        versionName = Realm.version.toString()
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    // FIXME connectedAndroidTest does not trigger any test. Tried to apply the workaround, but
    //  does not seem to work, maybe due to naming, ordering, etc. Workaround seemed to be working
    //  for Groovy build files. Have not investigated further.
    // https://youtrack.jetbrains.com/issue/KT-35016
    // https://github.com/realm/realm-kotlin/issues/73
    sourceSets.getByName("androidTest").java.srcDir(file("src/androidTest/kotlin"))
}
val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
    // TODO MPP-BUILD Defining ios target as iosX64 somehow hides the ioxX64 target. Can be removed when full
    //  ios builds are in place
    //  https://github.com/realm/realm-kotlin/issues/72
    val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    framework.compilation.kotlinOptions.freeCompilerArgs += listOf("-Xverbose-phases=Linker")
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
}
tasks.getByName("build").dependsOn(packForXcode)
