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
    // TODO Publish marker artifact to OJO to allow applying plugin by id
    //  https://github.com/realm/realm-kotlin/issues/100
    // Apply Realm Kotlin plugin even though we technically do not need it, to ensure that we have
    // the right kotlinOptions
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
dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.material:material:1.2.0")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
    // TODO AUTO-SETUP
    compileOnly("io.realm.kotlin:library:${Realm.version}")
}
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    defaultConfig {
        applicationId = "io.realm.example.kmmsample.androidApp"
        // FIXME Use Versions.Android.minSdk when it is aligned in the SDK
        minSdkVersion(21)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
        versionName = Realm.version
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

tasks.register("monkey", type = Exec::class) {
    val clearDataCommand = listOf("adb", "shell", "monkey", "-p", "io.realm.example.kmmsample.androidApp", "-v", "500", "--kill-process-after-error")
    commandLine(clearDataCommand)
}
