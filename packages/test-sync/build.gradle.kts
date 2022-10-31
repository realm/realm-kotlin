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
    kotlin("plugin.serialization") version Versions.kotlin
    // Test relies on the compiler plugin, but we cannot apply our full plugin from within the same
    // gradle run, so we just apply the compiler plugin directly as a dependency below instead
    // id("io.realm.kotlin")
}


// Test relies on the compiler plugin, but we cannot apply our full plugin from within the same
// gradle run, so we just apply the compiler plugin directly
dependencies {
    kotlinCompilerPluginClasspath("io.realm.kotlin:plugin-compiler:${Realm.version}")
    kotlinNativeCompilerPluginClasspath("io.realm.kotlin:plugin-compiler-shaded:${Realm.version}")
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
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                // FIXME AUTO-SETUP Removed automatic dependency injection to ensure observability of
                //  requirements for now
                implementation(project(":test-base"))
                // IDE Doesn't resolve library-base symbols if not adding it as an explicit
                // dependency. Probably due to our own custom dependency substitution above, but
                // shouldn't be an issue as it is already a transitive dependency of library-sync.
                implementation("io.realm.kotlin:library-base:${Realm.version}")
                implementation("io.realm.kotlin:library-sync:${Realm.version}")
                // FIXME API-SCHEMA We currently have some tests that verified injection of
                //  interfaces, uses internal representation for property meta data, etc. Can
                //  probably be replaced when schema information is exposed in the public API
                // Our current compiler plugin tests only runs on JVM, so makes sense to keep them
                // for now, but ideally they should go to the compiler plugin tests.
                implementation("io.realm.kotlin:cinterop:${Realm.version}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")

                // For server admin
                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-logging:${Versions.ktor}")
                implementation("io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}")
                implementation("io.ktor:ktor-client-content-negotiation:${Versions.ktor}")

                implementation("com.squareup.okio:okio:${Versions.okio}")
            }
        }

        val commonTest by getting {
            dependencies {
                // TODO AtomicFu doesn't work on the test project due to
                //  https://github.com/Kotlin/kotlinx.atomicfu/issues/90#issuecomment-597872907
                implementation("co.touchlab:stately-concurrency:1.2.0")
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        sourceSets {
            getByName("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
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
        val androidMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
            }
        }
        val androidTest by getting {
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
        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
                implementation("io.realm.kotlin:plugin-compiler:${Realm.version}")
                implementation("com.github.tschuchortdev:kotlin-compile-testing:${Versions.kotlinCompileTesting}")
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

kotlin {
    // define targets depending on the host platform (Apple or Intel)
    var macOsRunner = false
    if (System.getProperty("os.arch") == "aarch64") {
        iosSimulatorArm64("ios")
        macosArm64("macos")
        macOsRunner = true
    } else if (System.getProperty("os.arch") == "x86_64") {
        iosX64("ios")
        macosX64("macos")
        macOsRunner = true
    }
    targets.filterIsInstance<KotlinNativeTargetWithSimulatorTests>().forEach { simulatorTargets ->
        simulatorTargets.testRuns.forEach { testRun ->
            testRun.deviceId = project.findProperty("iosDevice")?.toString() ?: "iPhone 12"
        }
    }
    if (macOsRunner) {
        sourceSets {
            val commonMain by getting
            val commonTest by getting
            val nativeDarwin by creating {
                dependsOn(commonMain)
            }
            val nativeDarwinTest by creating {
                dependsOn(commonTest)
                // We cannot include this as it will generate duplicates
                // e: java.lang.IllegalStateException: IrPropertyPublicSymbolImpl for io.realm.kotlin.test.mongodb.util/TEST_METHODS|-1310682179529671403[0] is already bound: PROPERTY name:TEST_METHODS visibility:public modality:FINAL [val]
                // dependsOn(nativeDarwin)
            }
            val macosMain by getting { dependsOn(nativeDarwin) }
            val macosTest by getting { dependsOn(nativeDarwinTest) }
            val iosMain by getting { dependsOn(nativeDarwin) }
            val iosTest by getting { dependsOn(nativeDarwinTest) }
        }
    }
}
