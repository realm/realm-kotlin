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

    // Ensure that androidUnitTest uses the Realm JVM variant rather than Android.
    // This should cover both "debug" and "release" variants.
    //
    // WARNING: This does not work unless jvm artifacts has been published which also means
    // that Android JVM tests will not pickup changes to the library unless they are manually
    // published using `publishAllPublicationsToTestRepository`.
    //
    // See https://github.com/realm/realm-kotlin/issues/1404 for more details.
    if (name.endsWith("UnitTestRuntimeClasspath")) {
        resolutionStrategy.dependencySubstitution {
            substitute(module("io.realm.kotlin:library-base:${Realm.version}")).using(
                module("io.realm.kotlin:library-base-jvm:${Realm.version}")
            )
            substitute(module("io.realm.kotlin:cinterop:${Realm.version}")).using(
                module("io.realm.kotlin:cinterop-jvm:${Realm.version}")
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
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")
            }
        }

        val commonTest by getting {
            // Changes to dependencies must be duplicated in `androidInstrumentedTest` dependencies
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
            }
        }
        ndk {
            abiFilters += setOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
        }
    }

    buildTypes {
        // LibraryBuildType is not minifiable, but the current dependency from test-sync doesn't
        // allow test-base to be configured as a library. To test test-base with minification
        // change the AGP plugin to
        //    id("com.android.application")
        // and uncomment the below buildType then you can run the full test suite with
        //    ./gradlew test-base:clean test-base:connAT -PtestBuildType=debugMinified
        // Note that we cannot get memory consumption for non-debuggable build types so MemoryTests
        // will fail
        // testBuildType = (properties["testBuildType"] ?: "debug") as String
        // create("debugMinified") {
        //     initWith(getByName("debug"))
        //     matchingFallbacks.add("debug")
        //     isMinifyEnabled = true
        //     isDebuggable = false
        //     proguardFiles("proguard-rules-test.pro")
        // }
    }

    compileOptions {
        sourceCompatibility = Versions.sourceCompatibilityVersion
        targetCompatibility = Versions.targetCompatibilityVersion
    }

    // Remove overlapping resources after adding "org.jetbrains.kotlinx:kotlinx-coroutines-test" to
    // avoid errors like "More than one file was found with OS independent path 'META-INF/AL2.0'."
    packagingOptions {
        exclude("META-INF/AL2.0")
        exclude("META-INF/LGPL2.1")
    }
}

kotlin {
    androidTarget()
    sourceSets {
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")

                // Test dependencies shared between unitTest and instrumentedTest
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("junit:junit:${Versions.junit}")
                implementation("androidx.test.ext:junit:${Versions.androidxJunit}")
                implementation("androidx.test:runner:${Versions.androidxTest}")
                implementation("androidx.test:rules:${Versions.androidxTest}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
            }
        }
        val androidUnitTest by getting {
            dependencies {
                // Realm dependencies must be converted to -jvm variants here.
                // This is currently done using dependency substitution in `build.gradle`.
                // See https://kotlinlang.slack.com/archives/C19FD9681/p1685089661499199
            }
        }
        val androidInstrumentedTest by getting {
            // Instrumentation tests do not depend on commonTest by default:
            // https://kotlinlang.org/docs/whatsnew18.html#the-relation-between-android-and-common-tests
            // But adding support for this using `dependsOn(commonTest)` will prevent us
            // from selectively running unit tests on device from the IDE as the files do not
            // become visible in IntelliJ this way.
            //
            // In order to work around this limitation, the following strategy is used:
            //
            // 1. A symlink between all commonTest files and androidInstrumentedTest is created.
            //    This symlink is called `common` to mirror the package structure in commonTest.
            // 2. We need to duplicate all test dependencies from `commonTest` into
            //    `androidInstrumentedTest`.
            //
            // This approach results in a minimum amount of code changes and satisfies both our
            // IDE and CI requirements. But it also introduces the downside that we need to
            // duplicate dependencies between `androidInstrumentedTest` and `commonTest`
            //
            // Improvements to this situation is tracked here:
            // https://youtrack.jetbrains.com/issue/KT-46452/Allow-to-run-common-tests-as-Android-Instrumentation-tests

            // Copy of `commonTest` dependencies
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
}

kotlin {
    jvm()
    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation("io.realm.kotlin:plugin-compiler:${Realm.version}")
                implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${Versions.kotlin}")
                implementation("dev.zacsweers.kctfork:core:${Versions.kotlinCompileTesting}")
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
    if (HOST_OS == OperatingSystem.MACOS_ARM64) {
        iosSimulatorArm64("ios")
        macosArm64("macos")
    } else if (HOST_OS == OperatingSystem.MACOS_X64) {
        iosX64("ios")
        macosX64("macos")
    }
    targets.filterIsInstance<KotlinNativeTargetWithSimulatorTests>().forEach { simulatorTargets ->
        simulatorTargets.testRuns.forEach { testRun ->
            testRun.deviceId = project.findProperty("iosDevice")?.toString() ?: "iPhone 14"
        }
    }
    sourceSets {
        val commonMain by getting
        val commonTest by getting
        if (HOST_OS.isMacOs()) {
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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().all {
    kotlinOptions {
        freeCompilerArgs += listOf("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }
}

// Rules for getting Kotlin Native resource test files in place for locating it with the `assetFile`
// configuration. For JVM platforms the files are placed in
// `src/jvmTest/resources`(non-Android JVM) and `src/androidTest/assets` (Android).
if (HOST_OS.isMacOs()) {
    kotlin {
        targets.filterIsInstance<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithTests<*>>().forEach { simulatorTargets ->
            val target = simulatorTargets.name
            val testTaskName = "${target}Test"
            val testTask = tasks.findByName(testTaskName) ?: error("Cannot locate test task: '$testTaskName")
            val copyTask = tasks.register<Copy>("${target}TestResources") {
                from("src/${testTaskName}/resources")
                val parent = testTask.inputs.files.first().parent
                into(parent)
            }
            testTask.let {
                it.dependsOn(copyTask)
            }
        }
    }
}
