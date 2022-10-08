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

import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    // Test relies on the compiler plugin, but we cannot apply our full plugin from within the same
    // gradle run, so we just apply the compiler plugin directly as a dependency below instead
    // id("io.realm.kotlin")
}

// Test relies on the compiler plugin, but we cannot apply our full plugin from within the same
// gradle run, so we just apply the compiler plugin directly
dependencies {
    kotlinCompilerPluginClasspath("io.realm.kotlin:plugin-compiler:${Realm.version}")
    kotlinNativeCompilerPluginClasspath("io.realm.kotlin:plugin-compiler:${Realm.version}")
    kotlinCompilerClasspath("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
    kotlinCompilerClasspath("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:${Versions.kotlin}")
}

// Substitute maven coordinate dependencies of pattern 'io.realm.kotlin:<name>:${Realm.version}'
// with project dependency ':<name>' if '<name>' is configured as a subproject of the root project
configurations.all {
    resolutionStrategy.dependencySubstitution {
        rootProject.allprojects
            .filter { it != project && it != rootProject }
            .forEach { subproject: Project ->
                substitute(module("io.realm.kotlin:${subproject.name}:${Realm.version}")).using(
                    project(":${subproject.name}")
                )
            }
    }
}

// Common Kotlin configuration
kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                // FIXME AUTO-SETUP Removed automatic dependency injection to ensure observability of
                //  requirements for now
                implementation("io.realm.kotlin:library-base:${Realm.version}")
                // FIXME API-SCHEMA We currently have some tests that verified injection of
                //  interfaces, uses internal representation for property meta data, etc. Can
                //  probably be replaced when schema information is exposed in the public API
                // Our current compiler plugin tests only runs on JVM, so makes sense to keep them
                // for now, but ideally they should go to the compiler plugin tests.
                implementation("io.realm.kotlin:cinterop:${Realm.version}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
                implementation("com.squareup.okio:okio:${Versions.okio}")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
            }
        }

        getByName("commonTest") {
            dependencies {
                // TODO AtomicFu doesn't work on the test project due to
                //  https://github.com/Kotlin/kotlinx.atomicfu/issues/90#issuecomment-597872907
                implementation("co.touchlab:stately-concurrency:1.2.0")
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
            }
        }
    }

    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).all {
        kotlinOptions.jvmTarget = Versions.jvmTarget
        kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
    }
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        multiDexEnabled = true
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
        ndk {
            abiFilters += setOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    // Remove overlapping resources after adding "org.jetbrains.kotlinx:kotlinx-coroutines-test" to
    // avoid errors like "More than one file was found with OS independent path 'META-INF/AL2.0'."
    packagingOptions {
        exclude("META-INF/AL2.0")
        exclude("META-INF/LGPL2.1")
    }
}

kotlin {
    android("android") {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        getByName("androidMain") {
            kotlin.srcDir("src/androidMain/kotlin")
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
            }
        }
        getByName("androidTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("junit:junit:${Versions.junit}")
                implementation("androidx.test.ext:junit:${Versions.androidxJunit}")
                implementation("androidx.test:runner:${Versions.androidxTest}")
                implementation("androidx.test:rules:${Versions.androidxTest}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
                implementation("androidx.multidex:multidex:${Versions.multidex}")
            }
        }
    }
}

kotlin {
    jvm()
    sourceSets {
        getByName("jvmMain") {
            dependencies {
                implementation("io.realm.kotlin:plugin-compiler:${Realm.version}")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
                implementation("com.github.tschuchortdev:kotlin-compile-testing:${Versions.kotlinCompileTesting}")
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

kotlin {
    // define targets depending on the host platform (Apple or Intel)
    if(System.getProperty("os.arch") == "aarch64") {
        iosSimulatorArm64("ios")
        macosArm64("macos")
    } else if(System.getProperty("os.arch") == "x86_64") {
        iosX64("ios")
        macosX64("macos")
    }

    sourceSets {
        val macosMain by getting
        val macosTest by getting
        getByName("iosMain") {
            kotlin.srcDir("src/macosMain/kotlin")
        }
        getByName("iosTest") {
            kotlin.srcDir("src/macosTest/kotlin")
        }
    }
}

// Needs running emulator
tasks.named("iosTest") {
    val device: String = project.findProperty("iosDevice")?.toString() ?: "iPhone 11 Pro Max"
    dependsOn(kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").linkTaskName)
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Runs tests for target 'ios' on an iOS simulator"

    doLast {
        val binary = kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").outputFile
        exec {
            // use -s (standlone) option to avoid:
            //     An error was encountered processing the command (domain=com.apple.CoreSimulator.SimError, code=405):
            //      Invalid device state
            commandLine("xcrun", "simctl", "spawn", "-s", device, binary.absolutePath)
        }
    }
}
