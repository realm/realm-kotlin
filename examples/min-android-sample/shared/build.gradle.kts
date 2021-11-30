plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("io.realm.kotlin")
}

version = "1.0"

kotlin {
    jvm()
    android()

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation("io.realm.kotlin:library-base:${rootProject.ext["realmVersion"]}")
            }
        }
        val androidTest by getting
        val jvmMain by getting
    }
}

android {
    compileSdkVersion(31)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(31)
    }
}