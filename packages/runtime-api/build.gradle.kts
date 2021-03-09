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
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
}

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

detekt {
    input = files(file("src/androidMain/kotlin"), file("src/commonMain/kotlin"))
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1 // TODO What should we set this to, if anything?
        versionName = Realm.version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        sourceSets {
            getByName("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
                jniLibs.srcDir("src/androidMain/jniLibs")
                getByName("androidTest") {
                    java.srcDirs("src/androidTest/kotlin")
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    // See https://kotlinlang.org/docs/reference/mpp-publish-lib.html#publish-a-multiplatform-library
    // FIXME: We need to revisit this when we enable building on multiple hosts. Right now it doesn't do the right thing.
    configure(listOf(targets["metadata"], jvm())) {
        mavenPublication {
            val targetPublication = this@mavenPublication
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .all { onlyIf { findProperty("isMainHost") == "true" } }
        }
    }

    android("android") {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        getByName("androidMain") {
            kotlin.srcDir("src/androidMain/kotlin")
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
    jvm()
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    ios()
    sourceSets {
        getByName("iosMain") {
        }
    }
    macosX64("macos")
    sourceSets {
        getByName("macosMain") {
        }
    }
}

realmPublish {
    pom {
        name = "Runtime API"
        description = "Runtime API shared between Realm Kotlin compiler plugin and library code. This " +
            "artifact is not supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo { }
}
