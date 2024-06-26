import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("io.realm.kotlin")
}

version = "1.0"

kotlin {
    jvm()
    androidTarget()

    sourceSets {
        val commonMain by getting
        val commonTest by getting
        val androidMain by getting {
            dependencies {
                implementation("io.realm.kotlin:library-base:${rootProject.ext["realmVersion"]}")
            }
        }
        val androidInstrumentedTest by getting
        val jvmMain by getting
    }
}

android {
    compileSdk = 31
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = 16
        targetSdk = 31
    }
}

tasks.withType<KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }
}
