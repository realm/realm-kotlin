plugins {
    id("com.android.library")
    id("androidx.benchmark")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdk = Versions.Android.compileSdkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    defaultConfig {
        minSdk = Versions.Android.minSdk
        targetSdk = Versions.Android.targetSdk
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR,UNLOCKED"
        // Enable profiling. See https://developer.android.com/studio/profile/microbenchmark-profile
        // testInstrumentationRunnerArguments["androidx.benchmark.profiling.mode"] = "StackSampling"
    }

    testBuildType = "release"
    buildTypes {
        debug {
            // Since isDebuggable can"t be modified by gradle for library modules,
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
    androidTestImplementation("io.realm.kotlin:library-sync:${Realm.version}")
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