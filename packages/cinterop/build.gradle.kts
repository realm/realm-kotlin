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

repositories {
    google() // Android build needs com.android.tools.lint:lint-gradle:27.0.1
}

// TODO It is currently not possible to commonize user-defined libraries to use them across
//  platforms. To get IDE recognition of such code that is not yet commonized, we selectively add
//  the code only to one of the target platform's source set when 'idea.active' property
//  it true. This allows the IDE to resolve the symbols while still building correctly for all
//  platforms in other situations.
//  https://youtrack.jetbrains.com/issue/KT-40975
// TODO PROPOSAL Maybe make it possible to switch which platform the common parts are added to or
//  somehow derive a `isMainHost` property as proposed in
//  https://kotlinlang.org/docs/reference/mpp-publish-lib.html
//  Currently just adding the common darwin parts to macos-target.
val idea = System.getProperty("idea.active") == "true"

val corePath = "external/core"
val relativeCorePath = "../$corePath"
val cinteropCorePath = "$rootDir/$corePath"

android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion
    ndkVersion = Versions.Android.ndkVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionName = Realm.version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        sourceSets {
            val main by getting {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
                // Don't know how to set AndroidTest source dir, probably in its own source set by
                // "val test by getting" instead
                // androidTest.java.srcDirs += "src/androidTest/kotlin"
            }
        }
    }
    buildTypes {
        val release by getting {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    defaultConfig {
        ndk {
            abiFilters += setOf("x86_64", "arm64-v8a")
        }
        // Out externalNativeBuild (outside defaultConfig) does not seem to have correct type for setting cmake arguments
        externalNativeBuild {
            cmake {
                version = "${Versions.cmake}"
                arguments("-DANDROID_STL=c++_shared")
            }
        }
    }
    // Inner externalNativeBuild (inside defaultConfig) does not seem to have correct type for setting path
    externalNativeBuild {
        cmake {
            version = "${Versions.cmake}"
            path = project.file("src/jvmCommon/CMakeLists.txt")
        }
    }
}

val nativeLibraryIncludesMacosUniversal = listOf(
    "-include-binary", "$cinteropCorePath/capi_macos_universal/src/realm/object-store/c_api/librealm-ffi-static-dbg.a",
    "-include-binary", "$cinteropCorePath/capi_macos_universal/src/realm/librealm-dbg.a",
    "-include-binary", "$cinteropCorePath/capi_macos_universal/src/realm/parser/librealm-parser-dbg.a",
    "-include-binary", "$cinteropCorePath/capi_macos_universal/src/realm/object-store/librealm-object-store-dbg.a"
)
// TODO remove the debug suffix (-dbg) when switching to use release
val nativeLibraryIncludesIosArm64 = listOf(
    "-include-binary", "$cinteropCorePath/build-capi_ios_Arm64/lib/librealm-ffi-static-dbg.a",
    "-include-binary", "$cinteropCorePath/build-capi_ios_Arm64/lib/librealm-dbg.a",
    "-include-binary", "$cinteropCorePath/build-capi_ios_Arm64/lib/librealm-parser-dbg.a",
    "-include-binary", "$cinteropCorePath/build-capi_ios_Arm64/lib/librealm-object-store-dbg.a"
)
val nativeLibraryIncludesIosSimulatorUniversal = listOf(
    "-include-binary", "$cinteropCorePath/build-simulator_universal/lib/librealm-ffi-static-dbg.a",
    "-include-binary", "$cinteropCorePath/build-simulator_universal/lib/librealm-dbg.a",
    "-include-binary", "$cinteropCorePath/build-simulator_universal/lib/librealm-parser-dbg.a",
    "-include-binary", "$cinteropCorePath/build-simulator_universal/lib/librealm-object-store-dbg.a"
)

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
    // the def, but not across platforms in the current target  "hierarchy"
    // (https://kotlinlang.org/docs/reference/mpp-dsl-reference.html#targets)
    // FIXME MPP-BUILD If the native target is not specified fully (with architecture) the cinterop
    //  symbols won't be recognized by the IDE which is pretty annoying. Maybe fixed by only adding
    //  the source set to a single platform source set (see 'idea_active' definition in top of the
    //  file).
    // FIXME MPP-BUILD Relative paths in def-file resolves differently dependent of task entry point.
    //  https://youtrack.jetbrains.com/issue/KT-43439
    ios {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/nativeCommon/realm.def")
                packageName = "realm_wrapper"
                includeDirs(project.file("$relativeCorePath/src/"))
            }
            // Relative paths in def file depends are resolved differently dependent on execution
            // location
            // https://youtrack.jetbrains.com/issue/KT-43439
            // https://github.com/JetBrains/kotlin-native/issues/2314
            // ... and def file does not support using environment variables
            // https://github.com/JetBrains/kotlin-native/issues/3631
            // so resolving paths through gradle
            kotlinOptions.freeCompilerArgs += if (this.konanTarget == KonanTarget.IOS_ARM64) nativeLibraryIncludesIosArm64 else nativeLibraryIncludesIosSimulatorUniversal
        }
    }

    macosX64("macos") {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/nativeCommon/realm.def")
                packageName = "realm_wrapper"
                includeDirs(project.file("$relativeCorePath/src/"))
            }
            // Relative paths in def file depends are resolved differently dependent on execution
            // location
            // https://youtrack.jetbrains.com/issue/KT-43439
            // https://github.com/JetBrains/kotlin-native/issues/2314
            // ... and def file does not support using environment variables
            // https://github.com/JetBrains/kotlin-native/issues/3631
            // so resolving paths through gradle
            kotlinOptions.freeCompilerArgs += nativeLibraryIncludesMacosUniversal
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        // FIXME MPP-BUILD Maybe not the best idea to have in separate source set, but ideally we
        //  could reuse it for all JVM platform, seems like there are issues for the IDE to
        //  recognize symbols using this approach, but maybe a general thing (also issues with
        //  native cinterops)
        val jvmCommon by creating {
            dependsOn(commonMain)
            // IDE does not resolve 'jni-swig-module'-symbols in 'src/jvmCommon/kotlin/' if source
            // set is added here. Probably similar to issues around cinterop symbols not be
            // resolveable when added to both macos and ios platform. Current work around is to
            // add the common code explicitly to android and jvm source sets.
            // kotlin.srcDir("src/jvmCommon/kotlin")
            dependencies {
                api(project(":jni-swig-stub"))
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
            kotlin.srcDir("src/jvmCommon/kotlin")
        }

        val jvmMain by getting {
            dependsOn(jvmCommon)
            kotlin.srcDir("src/jvmCommon/kotlin")
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

        val darwinCommon by creating {
            dependsOn(commonMain)
            // Native symbols are not recognized correctly if platform is unknown when adding
            // source sets, so add common sources explicitly in "macosMain" and "iosMain" instead
        }

        val macosMain by getting {
            dependsOn(darwinCommon)
            kotlin.srcDir("src/darwinCommon/kotlin")
        }

        val iosMain by getting {
            dependsOn(darwinCommon)
            // Only add common sources to one platform when in the IDE. See comment at 'idea'
            // definition for full details.
            if (!idea) {
                kotlin.srcDir("src/darwinCommon/kotlin")
            }
        }

        val darwinTest by creating {
            dependsOn(darwinCommon)
            // Native symbols are not recognized correctly if platform is unknown when adding
            // source sets, so add common sources explicitly in "macosTest" and "iosTest" instead
        }

        val macosTest by getting {
            dependsOn(darwinTest)
            dependsOn(macosMain)
            kotlin.srcDir("src/darwinTest/kotlin")
        }

        val iosTest by getting {
            dependsOn(darwinTest)
            dependsOn(iosMain)
            // Only add common sources to one platform when in the IDE. See comment at 'idea'
            // difinition for full details.
            if (!idea) {
                kotlin.srcDir("src/darwinTest/kotlin")
            }
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
    configure(listOf(targets["metadata"], jvm())) {
        mavenPublication {
            val targetPublication = this@mavenPublication
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .all { onlyIf { findProperty("isMainHost") == "true" } }
        }
    }
}

// Building for Android [x86_64] & [arm64-v8a] ABIs
val capiAndroidDebug by tasks.registering {
    build_C_API_Android(releaseBuild = false)
}
val capiAndroidRelease by tasks.registering {
    build_C_API_Android(releaseBuild = true)
}
// Building Mach-O universal binary with 2 architectures: [x86_64] [arm64] (Apple M1) for macOS
val capiMacosUniversal by tasks.registering {
    build_C_API_Macos_Universal(releaseBuild = false) // TODO https://github.com/realm/realm-kotlin/issues/142 switch to Release build (with assertion enabled) once we reach Beta stability?
}
// Building Mach-O universal binary with 2 architectures: [x86_64] [arm64] (Apple M1) for iphone simulator
val capiSimulatorUniversal by tasks.registering {
    build_C_API_Simulator_Universal(releaseBuild = false)
}
// Building for ios device (arm64 only)
val capiIosArm64 by tasks.registering {
    build_C_API_iOS_Arm64(releaseBuild = false)
}

fun Task.build_C_API_Android(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    doLast {
        // x86_64
        exec {
            workingDir(project.file("$relativeCorePath"))
            commandLine("tools/cross_compile.sh", "-t", buildType, "-a", "x86_64", "-o", "android", "-f", "-DREALM_ENABLE_SYNC=0 -DREALM_NO_TESTS=ON")
            environment(mapOf("ANDROID_NDK" to android.ndkDirectory))
        }

        // arm64-v8a
        exec {
            workingDir(project.file("$relativeCorePath"))
            commandLine("tools/cross_compile.sh", "-t", buildType, "-a", "arm64-v8a", "-o", "android", "-f", "-DREALM_ENABLE_SYNC=0 -DREALM_NO_TESTS=ON")
            environment(mapOf("ANDROID_NDK" to android.ndkDirectory))
        }
    }
}

// There is no Interop tasks per build type yet AFAIK , but we can choose to switch between debug and release here
fun Task.build_C_API_Macos_Universal(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"

    doLast {
        exec {
            workingDir(project.file("$relativeCorePath"))
            commandLine("mkdir", "-p", "capi_macos_universal")
        }
        exec {
            commandLine("cmake", "-DCMAKE_TOOLCHAIN_FILE=../tools/cmake/macosx.toolchain.cmake", "-DCMAKE_BUILD_TYPE=$buildType", "-DREALM_ENABLE_SYNC=0", "-DREALM_NO_TESTS=1", "-DOSX_ARM64=1", "..")
            workingDir(project.file("$relativeCorePath/capi_macos_universal"))
        }
        exec {
            workingDir(project.file("$relativeCorePath/capi_macos_universal"))
            commandLine("cmake", "--build", ".", "-j8")
        }
    }
    outputs.file(project.file("$relativeCorePath/capi_macos_universal/src/realm/object-store/c_api/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/capi_macos_universal/src/realm/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/capi_macos_universal/src/realm/object-store/c_api/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/capi_macos_universal/src/realm/object-store/librealm-object-store$buildTypeSuffix.a"))
}

fun Task.build_C_API_Simulator_Universal(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"

    doLast {
        exec {
            workingDir(project.file("$relativeCorePath"))
            commandLine("mkdir", "-p", "build-simulator_universal")
        }
        exec {
            workingDir(project.file("$relativeCorePath/build-simulator_universal"))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=../tools/cmake/ios.toolchain.cmake",
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
            workingDir(project.file("$relativeCorePath/build-simulator_universal"))
            commandLine(
                "xcodebuild",
                "-sdk",
                "iphonesimulator",
                "-configuration",
                "Debug",
                "-target",
                "install", "ONLY_ACTIVE_ARCH=NO",
                "-UseModernBuildSystem=NO" // TODO remove flag when https://github.com/realm/realm-kotlin/issues/141 is fixed
            )
        }
    }
    outputs.file(project.file("$relativeCorePath/build-simulator_universal/lib/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-simulator_universal/lib/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-simulator_universal/lib/librealm-parser$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-simulator_universal/lib/librealm-object-store$buildTypeSuffix.a"))
}

fun Task.build_C_API_iOS_Arm64(releaseBuild: Boolean = false) {
    val buildType = if (releaseBuild) "Release" else "Debug"
    val buildTypeSuffix = if (releaseBuild) "" else "-dbg"

    doLast {
        exec {
            workingDir(project.file("$relativeCorePath"))
            commandLine("mkdir", "-p", "build-capi_ios_Arm64")
        }
        exec {
            workingDir(project.file("$relativeCorePath/build-capi_ios_Arm64"))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=../tools/cmake/ios.toolchain.cmake",
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
            workingDir(project.file("$relativeCorePath/build-capi_ios_Arm64"))
            commandLine(
                "xcodebuild",
                "-sdk",
                "iphoneos",
                "-configuration",
                "Debug",
                "-target",
                "install",
                "-arch",
                "arm64",
                "ONLY_ACTIVE_ARCH=NO",
                "-UseModernBuildSystem=NO"
            )
        }
    }
    outputs.file(project.file("$relativeCorePath/build-capi_ios_Arm64/lib/librealm-ffi-static$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-capi_ios_Arm64/lib/librealm$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-capi_ios_Arm64/lib/librealm-parser$buildTypeSuffix.a"))
    outputs.file(project.file("$relativeCorePath/build-capi_ios_Arm64/lib/librealm-object-store$buildTypeSuffix.a"))
}

afterEvaluate {
    tasks.named("externalNativeBuildDebug") {
        dependsOn(capiAndroidDebug)
    }
    tasks.named("externalNativeBuildRelease") {
        dependsOn(capiAndroidRelease)
    }
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
        description = "Wrapper for interacting with Realm Kotlin native code. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo { }
}
