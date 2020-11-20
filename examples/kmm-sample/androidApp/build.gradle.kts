plugins {
    id("com.android.application")
    kotlin("android")
    id("kotlin-android-extensions")
    // Apply Realm Kotlin plugin even though we technically do not need it, to ensure that we have
    // the right kotlinOptions
    id("realm-kotlin") version Realm.version
    // Apply Realm specific linting plugin to get common Realm linting tasks
    id("realm-lint")
}
group = "io.realm.example"
version = Realm.version

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
    compileSdkVersion(Versions.Android.compileSdkVersion)
    defaultConfig {
        applicationId = "io.realm.example.kmmsample.androidApp"
        // FIXME Use Versions.Android.minSdk when it is aligned in the SDK
        minSdkVersion(21)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
        versionName = Realm.version
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
