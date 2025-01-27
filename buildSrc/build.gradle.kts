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

// Add support for precompiled script plugins: https://docs.gradle.org/current/userguide/custom_plugins.html#sec:precompiled_plugins
plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
    kotlin("jvm") version Versions.kotlin
}

gradlePlugin {
    plugins {
        register("realm-publisher") {
            id = "realm-publisher"
            implementationClass = "org.realm.kotlin.RealmPublishPlugin"
        }
//        create("realm-compiler") { // Plugin name (used internally)
//            id = "io.realm.kotlin.plugin-compiler" // Plugin ID (used to apply)
//            implementationClass = "io.realm.kotlin.compiler.Registrar"
//        }
    }
}

java {
    sourceCompatibility = Versions.sourceCompatibilityVersion
    targetCompatibility = Versions.targetCompatibilityVersion
}

repositories {
    google()
    gradlePluginPortal()
}


// Setup dependencies for building the buildScript.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:${Versions.dokka}") // Use the latest version
    }
}

// Setup dependencies for the buildscripts consuming the precompiled plugins
// These seem to propagate to all projects including the buildSrc/ directory, which also means
// they are not allowed to set the version. It can only be set from here.
dependencies {
    implementation(kotlin("gradle-plugin", version = Versions.kotlin))
    implementation("io.github.gradle-nexus:publish-plugin:${Versions.nexusPublishPlugin}")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:${Versions.detektPlugin}")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${Versions.kotlin}")
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    implementation("org.gradle.kotlin:gradle-kotlin-dsl-plugins:5.1.2")
    implementation("org.jetbrains.dokka:dokka-gradle-plugin:${Versions.dokka}")
    implementation("com.android.tools:r8:${Versions.Android.r8}")
    implementation("com.android.tools.build:gradle:${Versions.Android.buildTools}")
    implementation(kotlin("script-runtime"))
}
