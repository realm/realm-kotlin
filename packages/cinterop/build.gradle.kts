plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    `maven-publish`
}

repositories {
    google() // Android build needs com.android.tools.lint:lint-gradle:27.0.1
}

group = Realm.group
version = Realm.version

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                api(project(":runtime-api"))
            }
        }
    }
}

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
                //androidTest.java.srcDirs += "src/androidTest/kotlin"
            }
        }
        ndk {
            // FIXME Extend supported platforms. Currently using local C API build and CMakeLists.txt only targeting x86_64
            abiFilters("x86_64")
        }
        // Out externalNativeBuild (outside defaultConfig) does not seem to have correct type for setting cmake arguments
        externalNativeBuild {
            cmake {
                arguments("-DANDROID_STL=c++_shared")
            }
        }

    }
    buildTypes {
        val release by getting {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    // Innter externalNativeBuild (inside defaultConfig) does not seem to have correct type for setting path
    externalNativeBuild {
        cmake {
            setPath("src/jvmCommon/jni/CMakeLists.txt")
        }
    }

}
kotlin {
    android("android") {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        // FIXME Maybe not the best idea to have in separate source set, but ideally we could reuse
        //  it for all JVM platform, seems like there are issues for the IDE to recognize symbols
        //  using this approach, but maybe a general thing (also issues with native cinterops)
        val jvmCommon by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/jvmCommon/kotlin")
        }
        val androidMain by getting {
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
        // FIXME Ideally we could reuse platform implementation just by using different cinterop
        //  klibs, but seems like IDE does not resolve symbols, which is a bit annoying
//        val nativeCommon by creating {
//            dependsOn(commonMain)
//            kotlin.srcDir("src/nativeCommon/kotlin")
//        }
        // FIXME Maybe it is some other IDE issue causing the IDE not recognizing symbols when
        //  using above, because now it also doesn't work when using it directly
        val iosMain by creating {
//            dependsOn(nativeCommon)
            kotlin.srcDir("src/iosMain/kotlin")
        }
    }
}

// Ios configuration
kotlin {
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64

    // We should be able to reuse configuration across different architectures (x86_64/arv differentiation can be done in def file)
    // FIXME Ideally we could reuse it across all native builds, but would have to do it dynamically
    //  as it does not seem like we can do this from the current target "hierarchy" (https://kotlinlang.org/docs/reference/mpp-dsl-reference.html#targets)
    iosX64("ios") {
        compilations.getByName("main") {
            cinterops.create("realm_wrapper") {
                defFile = project.file("src/nativeCommon/realm.def")
                packageName = "realm_wrapper"
                includeDirs("${project.projectDir}/../../external/core/src/realm")
            }
        }
    }
}

tasks.create("capi_android_x86_64") {
    doLast {
        exec {
            workingDir("../../external/core")
            commandLine("tools/cross_compile.sh", "-t", "Debug", "-a", "x86_64", "-o", "android", "-f", "-DREALM_NO_TESTS=ON")
            // FIXME Maybe use new android extension option to define and get NDK https://developer.android.com/studio/releases/#4-0-0-ndk-dir
            environment(mapOf("ANDROID_NDK" to System.getenv("ANDROID_HOME") + "/ndk/21.0.6113669"))
        }
    }
}
tasks.create("realmWrapperJvm") {
    doLast {
        exec {
            workingDir(".")
            // FIXME Use acutal realm.h file. Swig cannot handle the method prefix generated by
            //  #define RLM_EXPORT __attribute__((visibility("default"))),
            //  so did a local clone of the file.
            // TODO Maybe move to generated
            commandLine("swig", "-java", "-c++", "-I../../external/core/src/realm", "-o", "src/jvmCommon/jni/realmc.cpp", "-outdir", "src/jvmCommon/java", "realm.i")
        }
    }
    inputs.file("realm.i")
    outputs.dir("src/jvmCommon/java")
    outputs.dir("src/jvmCommon/jni")
}
tasks.named("preBuild") {
    dependsOn(tasks.named("realmWrapperJvm"))
    dependsOn(tasks.named("capi_android_x86_64"))
}


// FIXME/TODO Core build fails, so currently reusing macos build which prior to Xcode 12 can be used for both macos and ios simulator
tasks.create("capi_ios_simulator") {
    doLast {
        exec {
            workingDir("../../external/core")
            commandLine("tools/cross_compile.sh", "-t", "Debug", "-a", "x86_64", "-o", "android", "-f", "-DREALM_NO_TESTS=ON")
            // FIXME Maybe use new android extension option to define and get NDK https://developer.android.com/studio/releases/#4-0-0-ndk-dir
            environment(mapOf("ANDROID_NDK" to System.getenv("ANDROID_HOME") + "/ndk/21.0.6113669"))
        }
    }
}

tasks.create("capi_macos_x64") {
    doLast {
        exec {
            workingDir("../../external/core")
            commandLine("mkdir", "-p", "build-macos_x64")
        }
        exec {
            workingDir("../../external/core/build-macos_x64")
            commandLine("cmake", "-D", "CMAKE_BUILD_TYPE=debug", "..")
        }
        exec {
            workingDir("../../external/core/build-macos_x64")
            commandLine("cmake", "--build", ".", "-j8")
        }
    }
// TODO Fix inputs to prevent for proper incremental builds
//    inputs.dir("../../external/core/build-macos_x64")
    outputs.file("../../external/core/build-macos_x64/librealm-objectstore-wrapper-android-dynamic.so")
}

tasks.named("cinteropRealm_wrapperIos") {
    dependsOn(tasks.named("capi_macos_x64"))
}
