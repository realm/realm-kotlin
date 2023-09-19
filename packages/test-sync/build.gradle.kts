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
import com.codingfeline.buildkonfig.compiler.FieldSpec.Type

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    kotlin("plugin.serialization") version Versions.kotlin
    // Test relies on the compiler plugin, but we cannot apply our full plugin from within the same
    // gradle run, so we just apply the compiler plugin directly as a dependency below instead
    // id("io.realm.kotlin")
    id("com.codingfeline.buildkonfig") version Versions.buildkonfig
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

    // All kotlin compilation tasks
    tasks.withType(org.jetbrains.kotlin.gradle.dsl.KotlinCompile::class.java).all {
        kotlinOptions.freeCompilerArgs += listOf( "-P", "plugin:io.realm.kotlin:bundleId=TEST_BUNDLE_ID", )
    }
    // JVM specific KotlinCompilation tasks
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile::class.java).all {
        kotlinOptions.freeCompilerArgs += "-opt-in=kotlin.RequiresOptIn"
    }
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    testBuildType = (properties["testBuildType"] ?: "debug") as String

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
        create("debugMinified") {
            initWith(getByName("debug"))
            matchingFallbacks.add("debug")
            isMinifyEnabled = true
            isDebuggable = false
            proguardFiles("proguard-rules-test.pro")
        }
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
    android("android")
    sourceSets {
        val androidMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
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
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:${Versions.datetime}")
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
    if (HOST_OS == OperatingSystem.MACOS_ARM64) {
        iosSimulatorArm64("ios")
        macosArm64("macos")
    } else if (HOST_OS == OperatingSystem.MACOS_X64) {
        iosX64("ios")
        macosX64("macos")
    }
    targets.filterIsInstance<KotlinNativeTargetWithSimulatorTests>().forEach { simulatorTargets ->
        simulatorTargets.testRuns.forEach { testRun ->
            testRun.deviceId = project.findProperty("iosDevice")?.toString() ?: "iPhone 12"
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

// The Device Sync server used by the tests are configured through Gradle properties defined
// in `<root>/packages/gradle.properties`
// - 'syncTestUrl` defines the root URL for the App Services server. Default is `http://localhost:9090`
// - 'syncTestAppNamePrefix' is added a differentiator for all apps created by tests. This makes
//   it possible for builds in parallel to run against the same test server. Default is `test-app`.
buildkonfig {
    packageName = "io.realm.kotlin.test.mongodb"
    objectName = "SyncServerConfig"
    defaultConfigs {
        buildConfigField(Type.STRING, "url", properties["syncTestUrl"]!! as String)
        buildConfigField(Type.STRING, "appPrefix", properties["syncTestAppNamePrefix"]!! as String)
        if (properties.containsKey("syncTestLoginEmail") && properties.containsKey("syncTestLoginPassword")) {
            buildConfigField(Type.STRING, "email", properties["syncTestLoginEmail"]!! as String)
            buildConfigField(Type.STRING, "password", properties["syncTestLoginPassword"]!! as String)
        } else {
            buildConfigField(Type.STRING, "email", "")
            buildConfigField(Type.STRING, "password", "")
        }
        if (properties.containsKey("syncTestLoginPublicApiKey") && properties.containsKey("syncTestLoginPrivateApiKey")) {
            buildConfigField(Type.STRING, "publicApiKey", properties["syncTestLoginPublicApiKey"]!! as String)
            buildConfigField(Type.STRING, "privateApiKey", properties["syncTestLoginPrivateApiKey"]!! as String)
        } else {
            buildConfigField(Type.STRING, "publicApiKey", "")
            buildConfigField(Type.STRING, "privateApiKey", "")
        }
        buildConfigField(Type.STRING, "clusterName", properties["syncTestClusterName"] as String? ?: "")
    }
}

/**
 * Due to https://youtrack.jetbrains.com/issue/KT-38317/Kotlin-Native-NSURLConnection-HTTPS-requests-fail-in-iOS-tests-due-to-standalone-simctl-flag
 * We cannot currently use the default `iosTest` task when running against Cloud-QA.
 *
 * This task works around this by manually starting the simulator using `xcrun`. This has a few
 * implications.
 *
 * - It is difficult to only run a subset of tests as they require addition commandline parameters.
 *   Running an individual class can be done by adding `--ktest_gradle_filter=io.realm.kotlin.test.mongodb.shared.AppTests`
 *   to the shell command. These arguments where extracted by running `./gradlew iosTest --info`
 *   which displays the commandline arguments used by the standard test setup.
 *
 * - This command is mostly intended to run on Github Actions which start from a clean slate,
 *   so no attempt is done at tearing down the simulator. If this task is run locally, it might
 *   be needed to call `xcrun simctl shutdown 'iPhone 12'`. Otherwise the following error might
 *   be thrown when running this task:
 *
 *   ```
 *   An error was encountered processing the command (domain=com.apple.CoreSimulator.SimError, code=405):
 *   Unable to boot device in current state: Booted
 *   ```
 *
 * Note, this seems to be scheduled for a fix in 1.9.0.
 */
tasks.register("runCloudIosTests")  {
    val  device = project.findProperty("iosDevice") as? String ?: "iPhone 12"
    dependsOn("linkDebugTestIos")
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Runs tests targeting Cloud-QA for target 'ios' on an iOS simulator"

    // This is the output from the default iosTest task as of Kotlin 1.7.20:
    // /usr/bin/xcrun simctl spawn --standalone iPhone 12 /Users/cm/Realm/realm-kotlin-v3/packages/test-sync/build/bin/ios/debugTest/test.kexe -- --ktest_no_exit_code --ktest_logger=TEAMCITY --ktest_gradle_filter=io.realm.kotlin.test.mongodb.shared.AppTests
    // We mirror this setup and remove the --standalone flag, which is causing the issue. This
    // also means we manually have to boot the simulator.
    doLast {
        val  binary = (kotlin.targets["ios"] as org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget).binaries.getTest("DEBUG").outputFile
        exec {
            commandLine("sh", "-c", "xcrun simctl boot '$device' && xcrun simctl spawn '$device' ${binary.absolutePath} -- --ktest_logger=TEAMCITY")
        }
    }
}

// Rules for getting Kotlin Native resource test files in place for locating it with the `assetFile`
// configuration. For JVM platforms the files are placed in
// `src/jvmTest/resources`(non-Android JVM) and `src/androidTest/assets` (Android).
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
