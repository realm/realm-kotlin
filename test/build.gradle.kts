import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-kotlin")
    id("realm-lint")
}

repositories {
    google()
    jcenter()
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://dl.bintray.com/kotlin/kotlin-dev") }
}

// Common Kotlin configuration
kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }

        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
    }
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
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
                implementation(kotlin("stdlib"))
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
            }
        }
    }
}

kotlin {
    jvm()
    sourceSets {
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

kotlin {
    iosX64("ios") {
    }
    sourceSets {
        getByName("iosMain") { }
        getByName("iosTest") { }
    }
}

kotlin {
    macosX64("macos") {
    }
    sourceSets {
        getByName("macosMain") { }
        getByName("macosTest") { }
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
