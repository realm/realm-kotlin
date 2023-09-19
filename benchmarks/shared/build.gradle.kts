import io.realm.ClassGeneratorSpec
import io.realm.generate

plugins {
    kotlin("multiplatform")
    // kotlin("native.cocoapods")
    id("com.android.library")
    id("io.realm.kotlin")
}

version = "1.0"

kotlin {
    android()
    jvm()
// Disable iOS until needed
//    iosX64()
//    iosArm64()
//    iosSimulatorArm64()
//
//    cocoapods {
//        summary = "Some description for the Shared Module"
//        homepage = "Link to the Shared Module homepage"
//        ios.deploymentTarget = "14.1"
//        podfile = project.file("../iosApp/Podfile")
//        framework {
//            baseName = "shared"
//        }
//    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("io.realm.kotlin:library-sync:${Realm.version}")
            }
        }
        val main by creating {
            dependsOn(commonMain)
        }
        val androidMain by getting
// Disable iOS until needed
//        val iosX64Main by getting
//        val iosArm64Main by getting
//        val iosSimulatorArm64Main by getting
//        val iosMain by creating {
//            dependsOn(commonMain)
//            iosX64Main.dependsOn(this)
//            iosArm64Main.dependsOn(this)
//            iosSimulatorArm64Main.dependsOn(this)
//        }
//        val iosX64Test by getting
//        val iosArm64Test by getting
//        val iosSimulatorArm64Test by getting
//        val iosTest by creating {
//            dependsOn(commonTest)
//            iosX64Test.dependsOn(this)
//            iosArm64Test.dependsOn(this)
//            iosSimulatorArm64Test.dependsOn(this)
//        }
    }
}

abstract class ClassGenerator : DefaultTask() {
    @TaskAction
    fun generate() {
        val output = project.file("./src/commonMain/kotlin/")

        // Clear out any previous contents
        project.file("./src/commonMain/kotlin/io/realm/generated").deleteRecursively()

        ClassGeneratorSpec(
            classCount = 100,
            packageName = "io.realm.generated",
            className = "TenStringObject",
            stringFieldCount = 10
        ).generate(output)
    }
}

// Create a task using the task type
tasks.register<ClassGenerator>("classGen")

android {
    compileSdk = Versions.Android.compileSdkVersion
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Versions.Android.minSdk
        targetSdk = Versions.Android.targetSdk
    }
}