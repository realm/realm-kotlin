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
    // HACK On platforms that does not have an Android SDK we should skip trying to setup ndk build
    //  as this would cause the configuration phase to fail, while we don't even need the build
    if (includeAndroidBuild) {
        defaultConfig {
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
    // We should be able to reuse configuration across different architectures (x86_64/arv differentiation can be done in def file)
    // FIXME Ideally we could reuse it across all native builds, but would have to do it dynamically
    //  as it does not seem like we can do this from the current target "hierarchy" (https://kotlinlang.org/docs/reference/mpp-dsl-reference.html#targets)
    // FIXME If the native target is not specified fully (with architecture) the cinterop
    //  symbols won't be recognized by the IDE which is pretty annoying.
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

        // FIXME Maybe not the best idea to have in separate source set, but ideally we could reuse
        //  it for all JVM platform, seems like there are issues for the IDE to recognize symbols
        //  using this approach, but maybe a general thing (also issues with native cinterops)
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
            kotlin.srcDir("src/darwinCommon/kotlin")
        }

        val iosMain by getting {
            dependsOn(darwinCommon)
        }

        val macosMain by getting {
            dependsOn(darwinCommon)
        }

        val darwinTest by creating {
            dependsOn(darwinCommon)
            kotlin.srcDir("src/darwinTest/kotlin")
        }

        val macosTest by getting {
            dependsOn(darwinTest)
        }

        val iosTest by getting {
            dependsOn(darwinTest)
        }
    }

    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xopt-in=kotlin.ExperimentalUnsignedTypes")
            }
        }
    }
}

// Tasks for building capi...replace with Monorepo or alike when ready
tasks.create("capi_android_x86_64") {
    doLast {
        exec {
            workingDir(project.file("../../external/core"))
            commandLine("tools/cross_compile.sh", "-t", "Debug", "-a", "x86_64", "-o", "android", "-f", "-DREALM_NO_TESTS=ON")
            // FIXME Maybe use new android extension option to define and get NDK https://developer.android.com/studio/releases/#4-0-0-ndk-dir
            environment(mapOf("ANDROID_NDK" to android.ndkDirectory))
        }
    }
}

if (includeAndroidBuild) {
    afterEvaluate {
        tasks.named("externalNativeBuildDebug") {
            dependsOn(tasks.named("capi_android_x86_64"))
        }
        tasks.named("generateJsonModelDebug") {
            inputs.files(tasks.getByPath(":jni-swig-stub:realmWrapperJvm").outputs)
        }
    }
}

// FIXME/TODO Core build fails, so currently reusing macos build which prior to Xcode 12 can be used for both macos and ios simulator
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
// TODO Fix inputs to prevent for proper incremental builds
//    inputs.dir("../../external/core/build-macos_x64")
    outputs.file(project.file("../../external/core/build-macos_x64/librealm-objectstore-wrapper-android-dynamic.so"))
}

tasks.named("cinteropRealm_wrapperIos") {
    dependsOn(tasks.named("capi_macos_x64"))
}

tasks.named("cinteropRealm_wrapperMacos") {
    dependsOn(tasks.named("capi_macos_x64"))
}
