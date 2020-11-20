import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("kotlin-android-extensions")
    // Apply Realm Kotlin plugin
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
kotlin {
    android()
    // TODO Realm is not available for non-X64 hosts yet
    iosX64("ios") {
        binaries {
            framework {
                baseName = "shared"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                // FIXME Fetch version from realm repository
                implementation("io.realm.kotlin:library:0.0.1-SNAPSHOT")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val androidMain by getting {
            dependencies {
                implementation("com.google.android.material:material:1.2.0")
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("junit:junit:4.12")
            }
        }
        val iosMain by getting
        val iosTest by getting
    }
}
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
        versionCode = 1
        versionName = Realm.version.toString()
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    // FIXME connectedAndroidTest does not trigger any test. Tried to apply the workaround, but
    //  does not seem to work, maybe due to naming, ordering, etc. Workaround seemed to be working
    //  for Groovy build files. Have not investigated further.
    // https://youtrack.jetbrains.com/issue/KT-35016
    sourceSets.getByName("androidTest").java.srcDir(file("src/androidTest/kotlin"))
}
val packForXcode by tasks.creating(Sync::class) {
    group = "build"
    val mode = System.getenv("CONFIGURATION") ?: "DEBUG"
    val sdkName = System.getenv("SDK_NAME") ?: "iphonesimulator"
    // FIXME Defining ios target as iosX64 somehow hides the ioxX64 target
    // val targetName = "ios" + if (sdkName.startsWith("iphoneos")) "Arm64" else "X64"
    val targetName = "ios"
    val framework = kotlin.targets.getByName<KotlinNativeTarget>(targetName).binaries.getFramework(mode)
    inputs.property("mode", mode)
    dependsOn(framework.linkTask)
    val targetDir = File(buildDir, "xcode-frameworks")
    from({ framework.outputDirectory })
    into(targetDir)
}
tasks.getByName("build").dependsOn(packForXcode)
