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

plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android-extensions")
    // Apply Realm Kotlin plugin even though we technically do not need it, to ensure that we have
    // the right kotlinOptions
    id("io.realm.kotlin") version "0.1.0"
    // Apply Realm specific linting plugin to get common Realm linting tasks
    id("realm-lint")
}

group = "io.realm.example"
version = "0.1.0"

dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.material:material:1.2.0")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    // TODO AUTO-SETUP
    compileOnly("io.realm.kotlin:library:${version}")
}

android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    defaultConfig {
        applicationId = "io.realm.example.kmmsample.androidApp"
        // FIXME Use Versions.Android.minSdk when it is aligned in the SDK
        minSdkVersion(21)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
        versionName = "$version"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
