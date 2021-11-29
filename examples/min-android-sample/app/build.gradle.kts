plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    compileSdkVersion(31)
    defaultConfig {
        applicationId = "io.realm.example.minandroidsample.android"
        minSdkVersion(16)
        targetSdkVersion(31)
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("com.android.support:appcompat-v7:28.0.0")
    implementation("com.android.support.constraint:constraint-layout:2.0.4")
}