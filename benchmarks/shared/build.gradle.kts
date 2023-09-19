import com.android.build.gradle.internal.tasks.factory.dependsOn
import io.realm.ClassGeneratorSpec
import io.realm.BenchmarkClassSuite

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

// Create a task using the task type
val genClassesTask = tasks.create("classGen") {
    val output = project.file("./src/commonMain/kotlin/")

    // Clear out any previous contents
    project.file("./src/commonMain/kotlin/io/realm/generated").deleteRecursively()

    BenchmarkClassSuite(
        name = "openCloseRealm",
        packageName = "io.realm.generated",
        output = output,
    ) {
        addClassGeneratorSpec(
            classCount = 100,
            className = "OneString",
            stringFieldCount = 1,
        )
        addClassGeneratorSpec(
            classCount = 100,
            className = "OneStringRealmList",
            stringRealmListCount = 1,
        )
        addClassGeneratorSpec(
            classCount = 100,
            className = "TenStrings",
            stringFieldCount = 10,
        )
        addClassGeneratorSpec(
            classCount = 100,
            className = "TenStringRealmLists",
            stringRealmListCount = 10,
        )
        addClassGeneratorSpec(
            classCount = 100,
            className = "HundredStrings",
            stringFieldCount = 100,
        )
        addClassGeneratorSpec(
            classCount = 100,
            className = "HundredStringRealmLists",
            stringRealmListCount = 100,
        )
    }
}

afterEvaluate {
    tasks.named("assemble").dependsOn(genClassesTask.name)
}

android {
    compileSdk = Versions.Android.compileSdkVersion
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdk = Versions.Android.minSdk
        targetSdk = Versions.Android.targetSdk
    }
}