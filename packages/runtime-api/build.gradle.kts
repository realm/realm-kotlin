plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
}

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

detekt {
    input = files(file("src/androidMain/kotlin"), file("src/commonMain/kotlin"))
}

// Common Kotlin configuration
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
    }

    // See https://kotlinlang.org/docs/reference/mpp-publish-lib.html#publish-a-multiplatform-library
    // FIXME: We need to revisit this when we enable building on multiple hosts. Right now it doesn't do the right thing.
    configure(listOf(targets["metadata"], jvm())) {
        mavenPublication {
            val targetPublication = this@mavenPublication
            tasks.withType<AbstractPublishToMaven>()
                .matching { it.publication == targetPublication }
                .all { onlyIf { findProperty("isMainHost") == "true" } }
        }
    }
}

// JVM
kotlin {
    jvm()
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1 // TODO What should we set this to, if anything?
        versionName = Realm.version
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

        sourceSets {
            getByName("main") {
                manifest.srcFile("src/androidMain/AndroidManifest.xml")
                jniLibs.srcDir("src/androidMain/jniLibs")
                getByName("androidTest") {
                    java.srcDirs("src/androidTest/kotlin")
                }
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

kotlin {
    android("android") {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        getByName("androidMain") {
            kotlin.srcDir("src/androidMain/kotlin")
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }
    }
}

// IOS Configurastion
kotlin {
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    iosX64("ios")
    sourceSets {
        getByName("iosMain") {
        }
    }
}

// Macos configuration
kotlin {
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    macosX64("macos")
    sourceSets {
        getByName("macosMain") {
        }
    }
}

realmPublish {
    pom {
        name = "Runtime API"
        description = "Runtime API shared between Realm Kotlin compiler plugin and library code. This " +
            "artifact is not supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo {
        // List fetched from https://medium.com/vmware-end-user-computing/publishing-kotlin-multiplatform-artifacts-to-artifactory-maven-a283ae5912d6
        // TODO Unclear if we should name "iosArm64" and "macosX64" as well?
        publications = arrayOf("androidDebug", "androidRelease", "ios", "macos", "jvm", "kotlinMultiplatform", "metadata")
    }
}
