plugins {
    id("com.android.library")
    id("androidx.benchmark")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.realm.kotlin.benchmarks.android"
    testNamespace = "io.realm.kotlin.benchmarks.android.test"
    compileSdk = Versions.Android.compileSdkVersion

    compileOptions {
        sourceCompatibility = Versions.sourceCompatibilityVersion
        targetCompatibility = Versions.targetCompatibilityVersion
    }

    kotlinOptions {
        jvmTarget = Versions.kotlinJvmTarget
    }

    defaultConfig {
        // Use minSdk = 32 because minSdk = 33 is throwing build time warnings saying it isn't supported,
        // also we want to test performance against the latest release rather than the oldest.
        minSdk = 32
        targetSdk = Versions.Android.targetSdk
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,UNLOCKED"
        // Disable profiling. See https://developer.android.com/studio/profile/microbenchmark-profile
        testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "None"
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can't be modified by gradle for library modules,
            // it must be done in a manifest - see src/androidTest/AndroidManifest.xml
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "benchmark-proguard-rules.pro"
            )
        }
        release {
            isDefault = true
        }
    }
}

dependencies {
    androidTestImplementation("io.realm.kotlin:library-base:${Realm.version}")
    androidTestImplementation("androidx.test:runner:${Versions.androidxTest}")
    androidTestImplementation("androidx.test.ext:junit:${Versions.androidxJunit}")
    androidTestImplementation("junit:junit:${Versions.junit}")
    androidTestImplementation("androidx.benchmark:benchmark-junit4:${Versions.androidxBenchmarkPlugin}")
    // Add your dependencies here. Note that you cannot benchmark code
    // in an app module this way - you will need to move any code you
    // want to benchmark to a library module:
    // https://developer.android.com/studio/projects/android-library#Convert
    androidTestImplementation(project(":shared"))
}