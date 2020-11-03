plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    `maven-publish`
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
}

// JVM
kotlin {
    jvm()
}

// Android configuration
android {
    compileSdkVersion(29)
    buildToolsVersion = "29.0.2"

    defaultConfig {
        minSdkVersion(21)
        targetSdkVersion(29)
        versionCode = 1
        versionName = "1.0"
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

publishing {
    publications {
        register<MavenPublication>("RuntimeApi") {
            artifactId = Realm.runtimeApiId
            pom {
                name.set("Runtime API")
                description.set("Runtime API shared between Realm Kotlin compiler plugin and library code. This " +
                        "artifact is not supposed to be consumed directly, but through " +
                        "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead.")
                url.set(Realm.projectUrl)
                licenses {
                    license {
                        name.set(Realm.License.name)
                        url.set(Realm.License.url)
                    }
                }
                issueManagement {
                    name.set(Realm.IssueManagement.name)
                    url.set(Realm.IssueManagement.url)
                }
                scm {
                    connection.set(Realm.SCM.connection)
                    developerConnection.set(Realm.SCM.developerConnection)
                    url.set(Realm.SCM.url)
                }
            }
        }
    }
}
