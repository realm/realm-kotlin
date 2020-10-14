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
        val commonMain by getting {}
        val jvmCommon by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/jvmCommon/kotlin")
//            dependencies {
//                implementation(kotlin("stdlib"))
//            }
        }
        val androidMain by getting {
            dependsOn(jvmCommon)
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
        // FIXME If we define this separately then we do not get IDE assistance around the wrappers
        val nativeCommon by creating {
            dependsOn(commonMain)
            kotlin.srcDir("src/nativeCommon")
        }
    }
}

tasks.create("cinteropRealm_wrapper_x86_64") {
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
            // TODO Maybe move to generated
            commandLine("swig", "-java", "-I../../external/core/src/realm", "-o", "src/jvmCommon/jni/realmc.c", "-outdir", "src/jvmCommon/java", "realm.i")
        }
    }
    inputs.file("realm.i")
    outputs.dir("src/jvmCommon/java")
    outputs.dir("src/jvmCommon/jni")
}
tasks.named("preBuild") {
    dependsOn(tasks.named("realmWrapperJvm"))
    dependsOn(tasks.named("cinteropRealm_wrapper_x86_64"))
}
