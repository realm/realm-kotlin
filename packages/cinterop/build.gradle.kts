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

import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
}

buildscript {
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

// Directory for generated Version.kt holding VERSION constant
val versionDirectory = "$buildDir/generated/source/version/"

// Types of builds supported
enum class BuildType(val type: String, val buildDirSuffix: String) {
    DEBUG( type ="Debug", buildDirSuffix = "-dbg"),
    RELEASE( type ="Release", buildDirSuffix = "");
}

// CONFIGURATION is an env variable set by XCode or could be passed to the gradle task to force a certain build type
//               * Example: to force build a release
//               realm-kotlin/packages> CONFIGURATION=Release ./gradlew capiIosArm64
//               * to force build a debug (default BTW) use
//               realm-kotlin/packages> CONFIGURATION=Debug ./gradlew capiIosArm64
//               default is 'Release'
val buildType: BuildType = if ((System.getenv("CONFIGURATION") ?: "RELEASE").equals("Release", ignoreCase = true)) {
    BuildType.RELEASE
} else {
    BuildType.DEBUG
}


fun checkIfBuildingNativeLibs(task: Task, action: Task.() -> Unit) {
    // Whether or not to build the underlying native Realm Libs. Generally these are only
    // needed at runtime and thus can be ignored when only building the layers on top
    if (project.extra.properties["realm.kotlin.buildRealmCore"] == "true") {
        action(task)
    } else {
        logger.warn("Ignore building native libs")
    }
}

val corePath = "external/core"
val absoluteCorePath = "$rootDir/$corePath"
val jvmJniPath = "src/jvmMain/resources/jni"

fun includeBinaries(binaries: List<String>): List<String> {
    return binaries.flatMap { listOf("-include-binary", it) }
}
val nativeLibraryIncludesMacosUniversalRelease = includeBinaries(
    listOf(
        "object-store/c_api/Release/librealm-ffi-static.a",
        "Release/librealm.a",
        "parser/Release/librealm-parser.a",
        "object-store/Release/librealm-object-store.a",
    ).map { "$absoluteCorePath/build-macos_universal/src/realm/$it" }
)
val nativeLibraryIncludesMacosUniversalDebug = includeBinaries(
    listOf(
        "object-store/c_api/Debug/librealm-ffi-static-dbg.a",
        "Debug/librealm-dbg.a",
        "parser/Debug/librealm-parser-dbg.a",
        "object-store/Debug/librealm-object-store-dbg.a",
    ).map { "$absoluteCorePath/build-macos_universal-dbg/src/realm/$it" }
)
val releaseLibs = listOf(
    "librealm-ffi-static.a",
    "librealm.a",
    "librealm-parser.a",
    "librealm-object-store.a",
)
val debugLibs = listOf(
    "librealm-ffi-static-dbg.a",
    "librealm-dbg.a",
    "librealm-parser-dbg.a",
    "librealm-object-store-dbg.a",
)
val nativeLibraryIncludesIosArm64Debug =
    includeBinaries(debugLibs.map { "$absoluteCorePath/build-capi_ios_Arm64-dbg/lib/$it" })
val nativeLibraryIncludesIosArm64Release =
    includeBinaries(releaseLibs.map { "$absoluteCorePath/build-capi_ios_Arm64/lib/$it" })
val nativeLibraryIncludesIosSimulatorX86Debug =
    includeBinaries(debugLibs.map { "$absoluteCorePath/build-simulator-x86_64-dbg/lib/$it" })
val nativeLibraryIncludesIosSimulatorX86Release =
    includeBinaries(releaseLibs.map { "$absoluteCorePath/build-simulator-x86_64/lib/$it" })
val nativeLibraryIncludesIosSimulatorArm64Debug =
    includeBinaries(debugLibs.map { "$absoluteCorePath/build-simulator-arm64-dbg/lib/$it" })
val nativeLibraryIncludesIosSimulatorArm64Release =
    includeBinaries(releaseLibs.map { "$absoluteCorePath/build-simulator-arm64/lib/$it" })

@Suppress("UNUSED_VARIABLE")
kotlin {
    jvm()
    androidTarget {
        // Changing this will also requires an update to the publishCIPackages task
        // in /packages/build.gradle.kts
        publishLibraryVariants("release")
    }

    // Cinterops seems sharable across architectures (x86_64/arm) with option of differentiation in
    // the def, but not across platforms in the current target "hierarchy"
    // (https://kotlinlang.org/docs/reference/mpp-dsl-reference.html#targets)
    // FIXME MPP-BUILD Relative paths in def-file resolves differently dependent of task entry point.
    //  https://youtrack.jetbrains.com/issue/KT-43439
    iosX64 { // Shortcut for both iosArm64 and iosX64
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                definitionFile.set(project.file("src/native/realm.def"))
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
            kotlinOptions.freeCompilerArgs += when (buildType) {
                BuildType.DEBUG -> nativeLibraryIncludesIosSimulatorX86Debug
                BuildType.RELEASE -> nativeLibraryIncludesIosSimulatorX86Release
            }
        }
    }
    iosSimulatorArm64 {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                definitionFile.set(project.file("src/native/realm.def"))
                packageName = "realm_wrapper"
                includeDirs("$absoluteCorePath/src/")
            }
            kotlinOptions.freeCompilerArgs += when (buildType) {
                BuildType.DEBUG -> nativeLibraryIncludesIosSimulatorArm64Debug
                BuildType.RELEASE -> nativeLibraryIncludesIosSimulatorArm64Release
            }
        }
    }
    iosArm64 {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                definitionFile.set(project.file("src/native/realm.def"))
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
            kotlinOptions.freeCompilerArgs += when (buildType) {
                BuildType.DEBUG -> nativeLibraryIncludesIosArm64Debug
                BuildType.RELEASE -> nativeLibraryIncludesIosArm64Release
            }
        }
    }
    macosX64 {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                definitionFile.set(project.file("src/native/realm.def"))
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
            kotlinOptions.freeCompilerArgs += when(buildType) {
                BuildType.DEBUG -> nativeLibraryIncludesMacosUniversalDebug
                BuildType.RELEASE -> nativeLibraryIncludesMacosUniversalRelease
            }
        }
    }
    macosArm64 {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                definitionFile.set(project.file("src/native/realm.def"))
                packageName = "realm_wrapper"
                includeDirs("$absoluteCorePath/src/")
            }
            kotlinOptions.freeCompilerArgs += when(buildType) {
                BuildType.DEBUG -> nativeLibraryIncludesMacosUniversalDebug
                BuildType.RELEASE -> nativeLibraryIncludesMacosUniversalRelease
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                api("org.mongodb.kbson:kbson:${Versions.kbson}")
            }
            kotlin.srcDir(versionDirectory)
        }
        val commonTest by getting
        val jvm by creating {
            dependsOn(commonMain)
            dependencies {
                api(project(":jni-swig-stub"))
            }
        }
        val jvmMain by getting {
            dependsOn(jvm)
        }
        val androidMain by getting {
            dependsOn(jvm)
            dependencies {
                implementation("androidx.startup:startup-runtime:${Versions.androidxStartup}")
                implementation("com.getkeepsafe.relinker:relinker:${Versions.relinker}")
            }
        }
        val androidInstrumentedTest by getting {
            dependencies {
                implementation(kotlin("reflect"))
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("junit:junit:${Versions.junit}")
                implementation("androidx.test.ext:junit:${Versions.androidxJunit}")
                implementation("androidx.test:runner:${Versions.androidxTest}")
                implementation("androidx.test:rules:${Versions.androidxTest}")
            }
        }
        val nativeDarwin by creating {
            dependsOn(commonMain)
        }
        val nativeDarwinTest by creating {
            dependsOn(commonTest)
        }
        val iosArm64Main by getting {
            dependsOn(nativeDarwin)
        }
        val iosArm64Test by getting {
            dependsOn(nativeDarwinTest)
        }
        val iosX64Main by getting {
            dependsOn(nativeDarwin)
        }
        val iosX64Test by getting {
            dependsOn(nativeDarwinTest)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(nativeDarwin)
        }
        val iosSimulatorArm64Test by getting {
            dependsOn(nativeDarwinTest)
        }
        val macosX64Main by getting {
            dependsOn(nativeDarwin)
        }
        val macosArm64Main by getting {
            dependsOn(nativeDarwin)
        }
        val macosX64Test by getting {
            dependsOn(nativeDarwinTest)
        }
        val macosArm64Test by getting {
            dependsOn(nativeDarwinTest)
        }
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlin.ExperimentalUnsignedTypes")
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}

tasks.withType<KotlinNativeCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
    }
}

android {
    namespace = "io.realm.kotlin.internal.interop"
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
            abiFilters += setOf("x86_64", "x86", "arm64-v8a", "armeabi-v7a")
        }

        // Out externalNativeBuild (outside defaultConfig) does not seem to have correct type for setting cmake arguments
        externalNativeBuild {
            cmake {
                if (!HOST_OS.isWindows()) {
                    // CCache is not officially supported on Windows and there are problems
                    // using it with the Android NDK. So disable for now.
                    // See https://github.com/ccache/ccache/discussions/447 for more information.
                    arguments("-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
                    arguments("-DCMAKE_C_COMPILER_LAUNCHER=ccache")
                }
                targets.add("realmc")
            }
        }
    }

    // Inner externalNativeBuild (inside defaultConfig) does not seem to have correct type for setting path
    externalNativeBuild {
        cmake {
            // We need to grab cmake version from `cmake --version` on the path and set it here
            // otherwise the build system will use the one from the NDK
            @Suppress("UnstableApiUsage")
            version = project.providers.of(CmakeVersionProvider::class) {}.get()
            path = project.file("src/jvm/CMakeLists.txt")
        }
    }

    compileOptions {
        sourceCompatibility = Versions.sourceCompatibilityVersion
        targetCompatibility = Versions.targetCompatibilityVersion
    }
}

// Building Mach-O universal binary with 2 architectures: [x86_64] [arm64] (Apple M1) for macOS
if (HOST_OS.isMacOs()) {
    val capiMacosUniversal by tasks.registering {
        build_C_API_Macos_Universal(buildVariant = buildType)
    }

    // Building Simulator binaries for iosX64 (x86_64) and iosSimulatorArm64 (i.e Apple silicon arm64)
    val capiSimulatorX64 by tasks.registering {
        build_C_API_Simulator("x86_64", buildType)
    }

    // Building Simulator binaries for iosSimulatorArm64 (i.e Apple silicon arm64)
    val capiSimulatorArm64 by tasks.registering {
        build_C_API_Simulator("arm64", buildType)
    }

    // Building for ios device (arm64 only)
    val capiIosArm64 by tasks.registering {
        build_C_API_iOS_Arm64(buildType)
    }

    tasks.named("cinteropRealm_wrapperIosArm64") {
        checkIfBuildingNativeLibs(this) {
            dependsOn(capiIosArm64)
        }
    }

    tasks.named("cinteropRealm_wrapperIosX64") {
        checkIfBuildingNativeLibs(this) {
            dependsOn(capiSimulatorX64)
        }
    }

    tasks.named("cinteropRealm_wrapperIosSimulatorArm64") {
        checkIfBuildingNativeLibs(this) {
            dependsOn(capiSimulatorArm64)
        }
    }

    tasks.named("cinteropRealm_wrapperMacosX64") {
        checkIfBuildingNativeLibs(this) {
            dependsOn(capiMacosUniversal)
        }
    }

    tasks.named("cinteropRealm_wrapperMacosArm64") {
        checkIfBuildingNativeLibs(this) {
            dependsOn(capiMacosUniversal)
        }
    }
}

val buildJVMSharedLibs: TaskProvider<Task> by tasks.registering {
    if (HOST_OS.isMacOs()) {
        buildSharedLibrariesForJVMMacOs()
    } else if (HOST_OS.isWindows()) {
        buildSharedLibrariesForJVMWindows()
    } else {
        throw IllegalStateException("Building JVM libraries on this platform is not supported: $HOST_OS")
    }
}

/**
 * Task responsible for copying native files for all architectures into the correct location,
 * making the library ready for distribution.
 *
 * The task assumes that some other task already pre-built the binaries, making this task
 * mostly useful on CI.
 */
val copyJVMSharedLibs: TaskProvider<Task> by tasks.registering {
    val copyJvmABIs = project.hasProperty("realm.kotlin.copyNativeJvmLibs")
            && (project.property("realm.kotlin.copyNativeJvmLibs") as String).isNotEmpty()
    logger.info("Copy native Realm JVM libraries: $copyJvmABIs")
    if (copyJvmABIs) {
        val archs = (project.property("realm.kotlin.copyNativeJvmLibs") as String)
            .split(",")
            .map { it.trim() }
            .map { it.toLowerCase() }

        archs.forEach { arch ->
            when(arch) {
                "linux" -> {
                    // copy Linux pre-built binaries
                    project.file("$buildDir/realmLinuxBuild/librealmc.so")
                        .copyTo(project.file("$jvmJniPath/linux/librealmc.so"), overwrite = true)
                    outputs.file(project.file("$jvmJniPath/linux/librealmc.so"))
                }
                "macos" -> {
                    // copy MacOS pre-built binaries
                    project.file("$buildDir/realmMacOsBuild/librealmc.dylib")
                        .copyTo(project.file("$jvmJniPath/macos/librealmc.dylib"), overwrite = true)
                    outputs.file(project.file("$jvmJniPath/macos/librealmc.dylib"))
                }
                "windows" -> {
                    // copy Window pre-built binaries
                    project.file("$buildDir/realmWindowsBuild/Release/realmc.dll")
                        .copyTo(project.file("$jvmJniPath/windows/realmc.dll"), overwrite = true)
                    outputs.file(project.file("$jvmJniPath/windows/realmc.dll"))
                }
                else -> throw IllegalArgumentException("Unsupported platform for realm.kotlin.copyNativeJvmLibs: $arch")
            }
        }
    }
}

/**
 * Consolidate shared CMake flags used across all configurations
 */
fun getSharedCMakeFlags(buildType: BuildType, ccache: Boolean = true): Array<String> {
    // Any change to CMAKE properties here, should be reflected in GHA(.github/workflows/pr.yml), specifically
    // the `build_jvm_linux` and `build_jvm_windows` functions.
    val args = mutableListOf<String>()
    if (ccache) {
        args.add("-DCMAKE_CXX_COMPILER_LAUNCHER=ccache")
        args.add("-DCMAKE_C_COMPILER_LAUNCHER=ccache")
    }
    val cmakeBuildType: String = when(buildType) {
        BuildType.DEBUG -> "Debug"
        BuildType.RELEASE -> "Release"
    }
    with(args) {
        add("-DCMAKE_BUILD_TYPE=$cmakeBuildType")
        add("-DREALM_NO_TESTS=1")
        add("-DREALM_BUILD_LIB_ONLY=true")
        add("-DREALM_CORE_SUBMODULE_BUILD=true")
        // This will prevent exporting Core's symbols which is useful when combining for example the Swift SDK and Kotlin Multiplatform
        // in the same app. The generated dynamically linked shared Framework from Kotlin/Native can then be linked dynamically into the iOS app (!use_frameworks in Cocoapods)
        // or statically. This will also reduce the binary size a little bit (avoid storing metadata about exported symbols)
        add("-DCMAKE_CXX_VISIBILITY_PRESET=hidden")
    }
    return args.toTypedArray()
}

// JVM native libs are currently always built in Release mode.
fun Task.buildSharedLibrariesForJVMMacOs() {
    group = "Build"
    description = "Compile dynamic libraries loaded by the JVM fat jar for supported platforms."
    val directory = "$buildDir/realmMacOsBuild"

    doLast {
        exec {
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake",
                *getSharedCMakeFlags(BuildType.RELEASE),
                "-DCPACK_PACKAGE_DIRECTORY=..",
                "-DCMAKE_OSX_ARCHITECTURES=x86_64;arm64",
                project.file("src/jvm/")
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine("cmake", "--build", ".", "-j8")
        }

        // copy files (macos)
        exec {
            commandLine("mkdir", "-p", project.file("$jvmJniPath/macos"))
        }
        File("$directory/librealmc.dylib")
            .copyTo(project.file("$jvmJniPath/macos/librealmc.dylib"), overwrite = true)
    }

    inputs.dir(project.file("src/jvm"))
    inputs.dir(project.file("$absoluteCorePath/src"))
    outputs.file(project.file("$jvmJniPath/macos/librealmc.dylib"))
}

fun Task.buildSharedLibrariesForJVMWindows() {
    group = "Build"
    description = "Compile dynamic libraries loaded by the JVM fat jar for supported platforms."
    val directory = "$buildDir/realmWindowsBuild"

    doLast {
        file(directory).mkdirs()
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake",
                *getSharedCMakeFlags(BuildType.RELEASE, ccache = false),
                "-DCMAKE_GENERATOR_PLATFORM=x64",
                "-DCMAKE_SYSTEM_VERSION=8.1",
                "-DVCPKG_TARGET_TRIPLET=x64-windows-static",
                project.file("src/jvm/")
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine("cmake", "--build", ".", "--config", "Release")
        }

        // copy files (Windows)
        project.file("$jvmJniPath/windows").mkdirs()
        File("$directory/Release/realmc.dll")
            .copyTo(project.file("$jvmJniPath/windows/realmc.dll"), overwrite = true)
    }

    inputs.dir(project.file("$absoluteCorePath/src"))
    outputs.file(project.file("$jvmJniPath/windows/realmc.dll"))
}

fun Task.build_C_API_Macos_Universal(buildVariant: BuildType) {
    val directory = "$absoluteCorePath/build-macos_universal${buildVariant.buildDirSuffix}"
    doLast {
        exec {
            commandLine("mkdir", "-p", directory)
        }
        exec {
            // See https://github.com/realm/realm-core/blob/master/tools/build-cocoa.sh#L47
            // for source of these arguments.
            workingDir(project.file(directory))
            commandLine(
                "cmake",
                *getSharedCMakeFlags(buildVariant),
                "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/xcode.toolchain.cmake",
                "-DCMAKE_SYSTEM_NAME=Darwin",
                "-DCPACK_SYSTEM_NAME=macosx",
                "-DCPACK_PACKAGE_DIRECTORY=..",
                "-DCMAKE_OSX_ARCHITECTURES=x86_64;arm64",
                "-G",
                "Xcode",
                ".."
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "xcodebuild",
                "-destination",
                "generic/platform=macOS",
                "-sdk",
                "macosx",
                "-configuration",
                "${buildVariant.type}",
                "-UseModernBuildSystem=NO", // TODO remove flag when https://github.com/realm/realm-kotlin/issues/141 is fixed
                "DISABLE_MANUAL_TARGET_ORDER_BUILD_WARNING=YES",
            )
        }
    }
    inputs.dir(project.file("$absoluteCorePath/src"))
    outputs.file(project.file("$directory/src/realm/object-store/c_api/$buildVariant/librealm-ffi-static.a"))
    outputs.file(project.file("$directory/src/realm/$buildVariant/librealm.a"))
    outputs.file(project.file("$directory/src/realm/object-store/c_api/$buildVariant/librealm-ffi-static.a"))
    outputs.file(project.file("$directory/src/realm/object-store/$buildVariant/librealm-object-store.a"))
}

fun Task.build_C_API_Simulator(arch: String, buildType: BuildType) {
    val directory = "$absoluteCorePath/build-simulator-$arch${buildType.buildDirSuffix}"
    doLast {
        exec {
            workingDir(project.file(absoluteCorePath))
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/xcode.toolchain.cmake",
                *getSharedCMakeFlags(buildType),
                "-DCMAKE_INSTALL_PREFIX=.",
                "-G",
                "Xcode",
                ".."
            )
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "xcodebuild",
                "ARCHS=$arch",
                "-sdk",
                "iphonesimulator",
                "-configuration",
                buildType.type,
                "-target",
                "install",
                "-UseModernBuildSystem=NO", // TODO remove flag when https://github.com/realm/realm-kotlin/issues/141 is fixed
                "DISABLE_MANUAL_TARGET_ORDER_BUILD_WARNING=YES",
            )
        }
    }
    inputs.dir(project.file("$absoluteCorePath/src"))
    outputs.file(project.file("$directory/lib/librealm-ffi-static${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm-parser${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm-object-store${buildType.buildDirSuffix}.a"))
}

fun Task.build_C_API_iOS_Arm64(buildType: BuildType) {
    val directory = "$absoluteCorePath/build-capi_ios_Arm64${buildType.buildDirSuffix}"
    doLast {
        exec {
            commandLine("mkdir", "-p", directory)
        }
        exec {
            workingDir(project.file(directory))
            commandLine(
                "cmake", "-DCMAKE_TOOLCHAIN_FILE=$absoluteCorePath/tools/cmake/xcode.toolchain.cmake",
                *getSharedCMakeFlags(buildType),
                "-DCMAKE_INSTALL_PREFIX=.",
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
                buildType.type,
                "-target",
                "install",
                "-arch",
                "arm64",
                "ONLY_ACTIVE_ARCH=NO",
                "-UseModernBuildSystem=NO",
                "DISABLE_MANUAL_TARGET_ORDER_BUILD_WARNING=YES",
            )
        }
    }
    inputs.dir(project.file("$absoluteCorePath/src"))
    outputs.file(project.file("$directory/lib/librealm-ffi-static${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm-parser${buildType.buildDirSuffix}.a"))
    outputs.file(project.file("$directory/lib/librealm-object-store${buildType.buildDirSuffix}.a"))
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

tasks.named("jvmMainClasses") {
    checkIfBuildingNativeLibs(this) {
        dependsOn(buildJVMSharedLibs)
    }
    dependsOn(copyJVMSharedLibs)
}

tasks.named("jvmProcessResources") {
    checkIfBuildingNativeLibs(this) {
        dependsOn(buildJVMSharedLibs)
    }
    dependsOn(copyJVMSharedLibs)
}

// Add generic macosTest task that execute macos tests according to the current host architecture
if (HOST_OS.isMacOs()) {
    tasks.register("macosTest") {
        val arch = when (HOST_OS) {
            OperatingSystem.MACOS_ARM64 -> "Arm64"
            OperatingSystem.MACOS_X64 -> "X64"
            else -> throw IllegalStateException("Unsupported macOS architecture: $HOST_OS")
        }
        dependsOn(tasks.named("macos${arch}Test"))
    }
}

// Maven Central requires JavaDoc so add empty javadoc artifacts
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    // See https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
        artifact(javadocJar.get())
    }
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

// Generate code with version constant
val generateSdkVersionConstant: Task = tasks.create("generateSdkVersionConstant") {
    val outputDir = file(versionDirectory)

    inputs.property("version", project.version)
    outputs.dir(outputDir)

    doLast {
        val versionFile = file("$outputDir/io/realm/kotlin/internal/Version.kt")
        versionFile.parentFile.mkdirs()
        versionFile.writeText(
            """
            // Generated file. Do not edit!
            package io.realm.kotlin.internal
            public const val SDK_VERSION: String = "${project.version}"
            """.trimIndent()
        )
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.dsl.KotlinCompile<*>> {
    dependsOn(generateSdkVersionConstant)
}

tasks.named("clean") {
    doLast {
        delete(buildJVMSharedLibs.get().outputs)
        delete(project.file(".cxx"))
    }
}

// Provider that reads the version of cmake that is on the PATH
@Suppress("UnstableApiUsage")
abstract class CmakeVersionProvider : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations
    override fun obtain(): String? {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine("cmake", "--version")
            standardOutput = output
        }
        val cmakeOutput = String(output.toByteArray(), Charset.defaultCharset())
        val regex = "cmake version (?<version>[0-9\\.]*)".toRegex()
        val cmakeVersion = regex.find(cmakeOutput)?.groups?.get("version")
            ?: throw RuntimeException("Couldn't match cmake version from: '$cmakeOutput'")
        return cmakeVersion.value
    }
}

// enable execution optimizations for generateSdkVersionConstant
afterEvaluate {
    tasks.getByName("sourcesJar").dependsOn(generateSdkVersionConstant)
}
