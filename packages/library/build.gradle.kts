import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
}

repositories {
    google()
    jcenter()
    mavenCentral()
    mavenLocal()
}

kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(kotlin("reflect"))
                api(project(":cinterop"))
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
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
        versionCode = 1 // TODO: What should we set this to, if anything?
        versionName = Realm.version
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

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
            isMinifyEnabled = false
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
                api(project(":cinterop"))
            }
        }

        getByName("androidTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
                implementation("junit:junit:${Versions.junit}")
                implementation("androidx.test.ext:junit:${Versions.androidxJunit}")
                implementation("androidx.test:runner:${Versions.androidxTest}")
                implementation("androidx.test:rules:${Versions.androidxTest}")
                implementation(kotlin("reflect:${Versions.kotlin}"))
            }
        }
    }
}

kotlin {
    sourceSets {
        create("nativeCommon") {
            dependencies {
                api(project(":cinterop"))
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
    iosX64("ios") {}
    sourceSets {
        getByName("iosMain") {
            dependsOn(getByName("nativeCommon"))
        }
        getByName("iosTest") {
        }
    }
}

// Macos configuration
kotlin {
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    macosX64("macos") {}
    sourceSets {
        getByName("macosMain") {
            dependsOn(getByName("nativeCommon"))
        }
        getByName("macosTest") {
        }
    }
}

// Needs running emulator
tasks.named("iosTest") {
    val device: String = project.findProperty("iosDevice")?.toString() ?: "iPhone 11 Pro Max"
    dependsOn(kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").linkTaskName)
    group = JavaBasePlugin.VERIFICATION_GROUP
    description = "Runs tests for target 'ios' on an iOS simulator"

    doLast {
        val binary = kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").outputFile
        exec {
            commandLine("xcrun", "simctl", "spawn", device, binary.absolutePath)
        }
    }
}

realmPublish {
    pom {
        name = "Library"
        description = "Library code for Realm Kotlin. This artifact is not " +
                "supposed to be consumed directly, but through " +
                "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
    ojo {
        // List fetched from https://medium.com/vmware-end-user-computing/publishing-kotlin-multiplatform-artifacts-to-artifactory-maven-a283ae5912d6
        // TODO Unclear if we should name "iosArm64" and "macosX64" as well?
        publications = arrayOf("androidDebug", "androidRelease", "ios", "macos", "jvm", "kotlinMultiplatform", "metadata")
    }
}
