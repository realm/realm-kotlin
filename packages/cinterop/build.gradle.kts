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

// FIXME MPP-BUILD Disable Android build when the SDK is not avaiable to allow building native parts
//  on machines without the Android SDK. Should not be needed if we build anything on a single host
//  with Android SDK.
//  https://github.com/realm/realm-kotlin/issues/76
val includeAndroidBuild = System.getenv("ANDROID_HOME") != null

android {
    compileSdkVersion(29)
    buildToolsVersion = "29.0.2"

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        sourceSets {
            val main by getting {
                // Cannot set these on KMM kotlin source set
                java.srcDir("src/jvmCommon/java")
                jni.srcDir("src/jvmCommon/jni")
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
    // FIXME MPP-BUILD On platforms that does not have an Android SDK we should skip trying to
    //  setup ndk build as this would cause the configuration phase to fail, while we don't even
    //  need the build
    if (includeAndroidBuild) {
        defaultConfig {
            ndk {
                // FIXME MPP-BUILD Extend supported platforms. Currently using local C API build and CMakeLists.txt only targeting x86_64
                //  Issue-Android devices
                abiFilters("x86_64")
            }
            // Out externalNativeBuild (outside defaultConfig) does not seem to have correct type for setting cmake arguments
            externalNativeBuild {
                cmake {
                    arguments("-DANDROID_STL=c++_shared")
                }
            }
        }
        // Inner externalNativeBuild (inside defaultConfig) does not seem to have correct type for setting path
        externalNativeBuild {
            cmake {
                setPath("src/jvmCommon/CMakeLists.txt")
            }
        }
    }
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
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
    iosX64("ios") {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/nativeCommon/realm.def")
                packageName = "realm_wrapper"
                includeDirs(project.file("../../external/core/src/realm"))
            }
        }
    }
    macosX64("macos") {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/nativeCommon/realm.def")
                packageName = "realm_wrapper"
                includeDirs(project.file("../../external/core/src/realm"))
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":runtime-api"))
            }
        }

        // FIXME MPP-BUILD Maybe not the best idea to have in separate source set, but ideally we
        //  could reuse it for all JVM platform, seems like there are issues for the IDE to
        //  recognize symbols using this approach, but maybe a general thing (also issues with
        //  native cinterops)
        val jvmCommon by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/jvmCommon/kotlin")
            dependencies {
                api(project(":jni-swig-stub"))
            }
        }

        val androidMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("androidx.startup:startup-runtime:1.0.0")
            }
        }

        val jvmMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))

                implementation("junit:junit:4.12")
                implementation("com.android.support.test:runner:1.0.2")
                implementation("com.android.support.test:rules:1.0.2")
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
            // difinition for full details.
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
                freeCompilerArgs = listOf("-Xopt-in=kotlin.ExperimentalUnsignedTypes")
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

// Tasks for building capi...replace with Monorepo or alike when ready
tasks.create("capi_android_x86_64") {
    doLast {
        exec {
            workingDir(project.file("../../external/core"))
            commandLine("tools/cross_compile.sh", "-t", "Debug", "-a", "x86_64", "-o", "android", "-f", "-DREALM_ENABLE_SYNC=0 -DREALM_NO_TESTS=ON")
            environment(mapOf("ANDROID_NDK" to android.ndkDirectory))
        }
    }
}

if (includeAndroidBuild) {
    afterEvaluate {
        tasks.named("externalNativeBuildDebug") {
            dependsOn(tasks.named("capi_android_x86_64"))
        }
        // Ensure that Swig wrapper is generated before compiling the JNI layer. This task needs
        // the cpp file as it somehow processes the CMakeList.txt-file, but haven't dug up the
        // actuals
        tasks.named("generateJsonModelDebug") {
            inputs.files(tasks.getByPath(":jni-swig-stub:realmWrapperJvm").outputs)
        }
    }
}

// FIXME MPP-BUILD Core build for iOS fails, so currently reusing macos build which prior to Xcode
//  12 can be used for both macos and ios simulator
//  https://github.com/realm/realm-kotlin/issues/72
tasks.create("capi_macos_x64") {
    doLast {
        exec {
            workingDir(project.file("../../external/core"))
            commandLine("mkdir", "-p", "build-macos_x64")
        }
        exec {
            workingDir(project.file("../../external/core/build-macos_x64"))
            commandLine("cmake", "-DCMAKE_BUILD_TYPE=debug", "-DREALM_ENABLE_SYNC=0", "-DREALM_NO_TESTS=1", "..")
        }
        exec {
            workingDir(project.file("../../external/core/build-macos_x64"))
            commandLine("cmake", "--build", ".", "-j8")
        }
    }
// FIXME MPP-BUILD Fix inputs to prevent for proper incremental builds
//    inputs.dir("../../external/core/build-macos_x64")
    outputs.file(project.file("../../external/core/build-macos_x64/src/realm/object-store/c_api/librealm-ffi-static-dbg.a"))
}

tasks.named("cinteropRealm_wrapperIos") {
    dependsOn(tasks.named("capi_macos_x64"))
}

tasks.named("cinteropRealm_wrapperMacos") {
    dependsOn(tasks.named("capi_macos_x64"))
}

realmPublish {
    pom {
        name = "C Interop"
        description = "Wrapper for interacting with Realm Kotlin native code. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo {
        publications = arrayOf("androidDebug", "androidRelease", "ios", "macos", "jvm", "kotlinMultiplatform", "metadata")
    }
}
