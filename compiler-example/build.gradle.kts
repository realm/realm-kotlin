buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
        jcenter()
    }
    dependencies {
        classpath("io.realm:gradle-plugin:0.0.1-SNAPSHOT")
    }
}

plugins {
    kotlin("multiplatform") version "1.4.0"
    // TODO Have not found a way to fetch the plugin from ID, to enable using io.realm.Config properties
    // id ("io.realm.config")
    `maven-publish`
}

repositories {
    mavenLocal()
    mavenCentral()
    jcenter()
}

apply(plugin = "io.realm.compiler-plugin")

kotlin {
    jvm {
        compilations.all {
            kotlinOptions {
                kotlinOptions.jvmTarget = "1.8"
                kotlinOptions.useIR = true
            }
        }
    }
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    macosX64("macos")
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(kotlin("stdlib-jdk8"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
        val macosMain by getting {
        }
        val macosTest by getting {
        }
    }
}
