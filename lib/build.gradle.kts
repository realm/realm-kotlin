import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform") version "1.3.60"
}
repositories {
    mavenCentral()
}
group = "io.realm"
version = "0.0.1"

//apply plugin: 'maven-publish'
apply(plugin = "maven-publish")

kotlin {
    jvm("android")
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    iosX64("ios") {
        compilations.getByName("main") {
            val objectstore_wrapper by cinterops.creating {
            }
//            cinterops {
//                objectstore_wrapper {
//                    // Options to be passed to compiler by cinterop tool.
////                    compilerOpts '-I/Users/Nabil/Dev/realm/realm-kotlin-mpp/lib/cpp_engine -I/Applications/Xcode.app/Contents/Developer/Platforms/MacOSX.platform/Developer/SDKs/MacOSX.sdk/System/Library/Frameworks/Kernel.framework/Versions/A/Headers/'
//                }
//            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
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
                implementation(kotlin("stdlib"))
            }
        }
        val androidTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val iosMain by getting {
        }
        val iosTest by getting {
        }
    }


    tasks.register("iosTest")  {
        val  device = project.findProperty("iosDevice") as? String ?: "iPhone 11 Pro Max"
        dependsOn("linkDebugTestIos")
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Runs tests for target 'ios' on an iOS simulator"

        doLast {
            val  binary = (kotlin.targets["ios"] as KotlinNativeTarget).binaries.getTest("DEBUG").outputFile
            exec {
                commandLine("xcrun", "simctl", "spawn", "--standalone", device, binary.absolutePath)
            }
        }
    }
}