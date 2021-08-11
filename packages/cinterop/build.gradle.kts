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

import org.jetbrains.kotlin.konan.target.KonanTarget

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.atomicfu}")
    }
}
apply(plugin = "kotlinx-atomicfu")
// AtomicFu cannot transform JVM code. Throws
// ClassCastException: org.objectweb.asm.tree.InsnList cannot be cast to java.lang.Iterable
project.extensions.configure(kotlinx.atomicfu.plugin.gradle.AtomicFUPluginExtension::class) {
    transformJvm = false
}

repositories {
    google() // Android build needs com.android.tools.lint:lint-gradle:27.0.1
}

// CONFIGURATION is an env variable set by XCode or could be passed to the gradle task to force a certain build type
//               * Example: to force build a release
//               realm-kotlin/packages> CONFIGURATION=Release ./gradlew capiIosArm64
//               * to force build a debug (default BTW) use
//               realm-kotlin/packages> CONFIGURATION=Debug ./gradlew capiIosArm64
//               default is 'Release'
val isReleaseBuild: Boolean = (System.getenv("CONFIGURATION") ?: "RELEASE").equals("Release", ignoreCase = true)

val corePath = "external/core"
val absoluteCorePath = "$rootDir/$corePath"

fun includeBinaries(binaries: List<String>): List<String> {
    return binaries.flatMap { listOf("-include-binary", it) }
}
val nativeLibraryIncludesMacosUniversalRelease = includeBinaries(
    listOf(
        "object-store/c_api/librealm-ffi-static.a",
        "librealm.a",
        "parser/librealm-parser.a",
        "object-store/librealm-object-store.a"
    ).map { "$absoluteCorePath/build-macos_universal/src/realm/" + it }
)
val nativeLibraryIncludesMacosUniversalDebug = includeBinaries(
    listOf(
        "object-store/c_api/librealm-ffi-static-dbg.a",
        "librealm-dbg.a",
        "parser/librealm-parser-dbg.a",
        "object-store/librealm-object-store-dbg.a"
    ).map { "$absoluteCorePath/build-macos_universal-dbg/src/realm/" + it }
)
val releaseLibs = listOf(
    "librealm-ffi-static.a",
    "librealm.a",
    "librealm-parser.a",
    "librealm-object-store.a"
)
val debugLibs = listOf(
    "librealm-ffi-static-dbg.a",
    "librealm-dbg.a",
    "librealm-parser-dbg.a",
    "librealm-object-store-dbg.a"
)
val nativeLibraryIncludesIosArm64Debug =
    includeBinaries(debugLibs.map { "$absoluteCorePath/build-capi_ios_Arm64-dbg/lib/" + it })
val nativeLibraryIncludesIosArm64Release =
    includeBinaries(releaseLibs.map { "$absoluteCorePath/build-capi_ios_Arm64/lib/" + it })
val nativeLibraryIncludesIosSimulatorUniversalDebug =
    includeBinaries(debugLibs.map { "$absoluteCorePath/build-simulator_universal-dbg/lib/" + it })
val nativeLibraryIncludesIosSimulatorUniversalRelease =
    includeBinaries(releaseLibs.map { "$absoluteCorePath/build-simulator_universal/lib/" + it })

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = Versions.jvmTarget
        }
    }
    android("android") {
        publishLibraryVariants("release", "debug")
    }
    // Cinterops seems sharable across architectures (x86_64/arm) with option of differentiation in
    // the def, but not across platforms in the current target "hierarchy"
    // (https://kotlinlang.org/docs/reference/mpp-dsl-reference.html#targets)
    // FIXME MPP-BUILD Relative paths in def-file resolves differently dependent of task entry point.
    //  https://youtrack.jetbrains.com/issue/KT-43439
    ios {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/native/realm.def")
                packageName = "realm_wrapper"
                includeDirs("$absoluteCorePath/src/")
            }
            // Relative paths in def file depends are resolved differently dependent on execution
            // location
            // https://youtrack.jetbrains.com/issue/KT-43439
            // https://github.com/JetBrains/kotlin-native/issues/2314
            // ... and def file does not support using environment variables
            // https://github.com/JetBrains/kotlin-native/issues/3631
            // so resolving paths through gradle
            kotlinOptions.freeCompilerArgs += if (this.konanTarget == KonanTarget.IOS_ARM64) {
                if (isReleaseBuild) nativeLibraryIncludesIosArm64Release else nativeLibraryIncludesIosArm64Debug
            } else {
                if (isReleaseBuild) nativeLibraryIncludesIosSimulatorUniversalRelease else nativeLibraryIncludesIosSimulatorUniversalDebug
            }
        }
    }

    macosX64("macos") {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/native/realm.def")
                packageName = "realm_wrapper"
                includeDirs("$absoluteCorePath/src/")
            }
            // Relative paths in def file depends are resolved differently dependent on execution
            // location
            // https://youtrack.jetbrains.com/issue/KT-43439
            // https://github.com/JetBrains/kotlin-native/issues/2314
            // ... and def file does not support using environment variables
            // https://github.com/JetBrains/kotlin-native/issues/3631
            // so resolving paths through gradle
            kotlinOptions.freeCompilerArgs += if (isReleaseBuild) nativeLibraryIncludesMacosUniversalRelease else nativeLibraryIncludesMacosUniversalDebug
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
            }
        }
        // FIXME HIERARCHICAL-BUILD Rename to jvm
        val jvm by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/jvm/kotlin")
            dependencies {
                api(project(":jni-swig-stub"))
            }
        }
        val jvmMain by getting {
            dependsOn(jvm)
        }
        val androidMain by getting {
            dependsOn(jvm)
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("junit:junit:${Versions.junit}")
                implementation("androidx.test.ext:junit:${Versions.androidxJunit}")
                implementation("androidx.test:runner:${Versions.androidxTest}")
                implementation("androidx.test:rules:${Versions.androidxTest}")
            }
        }
        val macosMain by getting {
            // TODO HIERARCHICAL-BUILD From 1.5.30-M1 we should be able to commonize cinterops using
            //  kotlin.mpp.enableCInteropCommonization=true (https://youtrack.jetbrains.com/issue/KT-40975)
            //  This would also require us to enable hierarchical setup, which is currently blocked by
            //  https://youtrack.jetbrains.com/issue/KT-48153
            // FIXME HIERARCHICAL-BUILD Rename to nativeDarwin
            kotlin.srcDir("src/darwin/kotlin")
        }
        val iosMain by getting {
            // TODO HIERARCHICAL-BUILD From 1.5.30-M1 we should be able to commonize cinterops using
            //  kotlin.mpp.enableCInteropCommonization=true (https://youtrack.jetbrains.com/issue/KT-40975)
            //  This would also require us to enable hierarchical setup, which is currently blocked by
            //  https://youtrack.jetbrains.com/issue/KT-48153
            kotlin.srcDir("src/darwin/kotlin")
        }
        val macosTest by getting {
            // FIXME HIERARCHICAL-BUILD Rename to nativeDarwinTest
            kotlin.srcDir("src/darwinTest/kotlin")
        }
        val iosTest by getting {
            kotlin.srcDir("src/darwinTest/kotlin")
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs += listOf("-Xopt-in=kotlin.ExperimentalUnsignedTypes")
            }
        }
    }

    // See https://kotlinlang.org/docs/reference/mpp-publish-lib.html#publish-a-multiplatform-library
    // FIXME MPP-BUILD We need to revisit this when we enable building on multiple hosts. Right now it doesn't do the right thing.
//    configure(listOf(targets["metadata"], jvm())) {
//        mavenPublication {
//            val targetPublication = this@mavenPublication
//            tasks.withType<AbstractPublishToMaven>()
//                .matching { it.publication == targetPublication }
//                .all { onlyIf { findProperty("isMainHost") == "true" } }
//        }
//    }
}

android {
    compileSdk = Versions.Android.compileSdkVersion
    buildToolsVersion = Versions.Android.buildToolsVersion
    ndkVersion = Versions.Android.ndkVersion

    defaultConfig {
        minSdk = Versions.Android.minSdk
        targetSdk = Versions.Android.targetSdk
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        sourceSets {
            getByName("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
                // Don't know how to set AndroidTest source dir, probably in its own source set by
                // "val test by getting" instead
                // androidTest.java.srcDirs += "src/androidTest/kotlin"
            }
        }

        ndk {
            abiFilters += setOf("x86_64", "arm64-v8a")
        }

        // Out externalNativeBuild (outside defaultConfig) does not seem to have correct type for setting cmake arguments
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
                targets.add("realmc")
            }
        }
    }

    // Inner externalNativeBuild (inside defaultConfig) does not seem to have correct type for setting path
    externalNativeBuild {
        cmake {
            version = Versions.cmake
            path = project.file("src/jvm/CMakeLists.txt")
        }
    }
    // To avoid
    // Failed to transform kotlinx-coroutines-core-jvm-1.5.0-native-mt.jar ...
    // The dependency contains Java 8 bytecode. Please enable desugaring by adding the following to build.gradle
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

// Building Mach-O universal binary with 2 architectures: [x86_64] [arm64] (Apple M1) for macOS
val capiMacosUniversal by tasks.registering {
    build_C_API_Macos_Universal(releaseBuild = isReleaseBuild)
}
// Building Mach-O universal binary with 2 architectures: [x86_64] [arm64] (Apple M1) for iphone simulator
val capiSimulatorUniversal by tasks.registering {
    build_C_API_Simulator_Universal(releaseBuild = isReleaseBuild)
}
// Building for ios device (arm64 only)
val capiIosArm64 by tasks.registering {
    build_C_API_iOS_Arm64(releaseBuild = isReleaseBuild)
}

fun Task.build_C_API_Macos_Universal(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"

    val directory = "$absoluteCorePath/build-macos_universal$buildTypeSuffix"
    doLast {
        exec {
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake",
                "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/macosx.toolchain.cmake",
                "-DCMAKE_BUILD_TYPE=$buildType",
                "-DREALM_ENABLE_SYNC=0",
                "-DREALM_NO_TESTS=1",
                "-DOSX_ARM64=1",
                ".."
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine("cmake", "--build", ".", "-j8")
        }
    }
    outputs.file(project.file("$directory/src/realm/object-store/c_api/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/src/realm/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/src/realm/object-store/c_api/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/src/realm/object-store/librealm-object-store$buildTypeSuffix.a"))
}

fun Task.build_C_API_Simulator_Universal(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"

    val directory = "$absoluteCorePath/build-simulator_universal$buildTypeSuffix"
    doLast {
        exec {
            workingDir(project.file(absoluteCorePath))
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/ios.toolchain.cmake",
                "-DCMAKE_INSTALL_PREFIX=.",
                "-DCMAKE_BUILD_TYPE=$buildType",
                "-DREALM_NO_TESTS=1",
                "-DREALM_ENABLE_SYNC=0",
                "-DREALM_NO_TESTS=ON",
                "-G",
                "Xcode",
                ".."
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "xcodebuild",
                "-sdk",
                "iphonesimulator",
                "-configuration",
                "$buildType",
                "-target",
                "install", "ONLY_ACTIVE_ARCH=NO",
                "-UseModernBuildSystem=NO" // TODO remove flag when https://github.com/realm/realm-kotlin/issues/141 is fixed
            )
        }
    }
    outputs.file(project.file("$directory/lib/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm-parser$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm-object-store$buildTypeSuffix.a"))
}

fun Task.build_C_API_iOS_Arm64(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"
    val directory = "$absoluteCorePath/build-capi_ios_Arm64$buildTypeSuffix"

    doLast {
        exec {
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/ios.toolchain.cmake",
                "-DCMAKE_INSTALL_PREFIX=.",
                "-DCMAKE_BUILD_TYPE=$buildType",
                "-DREALM_NO_TESTS=1",
                "-DREALM_ENABLE_SYNC=0",
                "-DREALM_NO_TESTS=ON",
                "-G",
                "Xcode",
                ".."
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "xcodebuild",
                "-sdk",
                "iphoneos",
                "-configuration",
                buildType,
                "-target",
                "install",
                "-arch",
                "arm64",
                "ONLY_ACTIVE_ARCH=NO",
                "-UseModernBuildSystem=NO"
            )
        }
    }
    outputs.file(project.file("$directory/lib/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm-parser$buildTypeSuffix.a"))
    outputs.file(project.file("$directory/lib/librealm-object-store$buildTypeSuffix.a"))
}

afterEvaluate {
    // Ensure that Swig wrapper is generated before compiling the JNI layer. This task needs
    // the cpp file as it somehow processes the CMakeList.txt-file, but haven't dug up the
    // actuals
    tasks.named("generateJsonModelDebug") {
        inputs.files(tasks.getByPath(":jni-swig-stub:realmWrapperJvm").outputs)
    }
    tasks.named("generateJsonModelRelease") {
        inputs.files(tasks.getByPath(":jni-swig-stub:realmWrapperJvm").outputs)
    }
}

tasks.named("cinteropRealm_wrapperIosX64") { // TODO is this the correct arch qualifier for OSX-ARM64? test on M1
    dependsOn(capiSimulatorUniversal)
}

tasks.named("cinteropRealm_wrapperIosArm64") {
    dependsOn(capiIosArm64)
}

tasks.named("cinteropRealm_wrapperMacos") {
    dependsOn(capiMacosUniversal)
}

realmPublish {
    pom {
        name = "C Interop"
        description =
            "Wrapper for interacting with Realm Kotlin native code. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}
