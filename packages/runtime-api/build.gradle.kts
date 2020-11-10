plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    `maven-publish`
    id("com.jfrog.artifactory")
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
    publications.withType<MavenPublication>().all {
        pom {
            name.set("Runtime API")
            description.set(
                "Runtime API shared between Realm Kotlin compiler plugin and library code. This " +
                    "artifact is not supposed to be consumed directly, but through " +
                    "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
            )
            url.set(Realm.projectUrl)
            licenses {
                license {
                    name.set(Realm.License.name)
                    url.set(Realm.License.url)
                }
            }
            issueManagement {
                system.set(Realm.IssueManagement.system)
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

artifactory {
    setContextUrl("https://oss.jfrog.org/artifactory")
    publish(
        delegateClosureOf<org.jfrog.gradle.plugin.artifactory.dsl.PublisherConfig> {
            repository(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    setProperty("repoKey", "oss-snapshot-local")
                    setProperty("username", if (project.hasProperty("bintrayUser")) project.properties["bintrayUser"] else "noUser")
                    setProperty("password", if (project.hasProperty("bintrayKey")) project.properties["bintrayKey"] else "noKey")
                }
            )
            defaults(
                delegateClosureOf<groovy.lang.GroovyObject> {
                    // List fetched from https://medium.com/vmware-end-user-computing/publishing-kotlin-multiplatform-artifacts-to-artifactory-maven-a283ae5912d6
                    // TODO Unclear if we should name "iosArm64" and "macosX64" as well?
                    invokeMethod(
                        "publications",
                        arrayOf(
                            "androidDebug", "androidRelease", "ios", "macos", "jvm", "kotlinMultiplatform", "metadata"
                        )
                    )
                }
            )
        }
    )
}
