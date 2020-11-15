plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android-extensions")
    // Apply Realm specific linting plugin to get common Realm linting tasks
    id("realm-lint")
}
group = "com.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    gradlePluginPortal()
    google()
    jcenter()
    mavenCentral()
}
dependencies {
    implementation(project(":shared"))
    implementation("com.google.android.material:material:1.2.0")
    implementation("androidx.appcompat:appcompat:1.2.0")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")
}
android {
    compileSdkVersion(29)
    defaultConfig {
        applicationId = "io.realm.example.kmmsample.androidApp"
        minSdkVersion(24)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

// FIXME Can be remove if we apply realm-kotlin plugin
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.useIR = true
}
