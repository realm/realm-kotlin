import io.realm.getPropertyValue

/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("realm-publisher")
    id("org.jetbrains.dokka") version Versions.dokka
}

repositories {
    google()
    jcenter()
    mavenCentral()
    mavenLocal()
}

// Common Kotlin configuration
kotlin {
    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(kotlin("reflect"))
                // If runtimeapi is merged with cinterop then we will be exposing both to the users
                // Runtime holds annotations, etc. that has to be exposed to users
                // Cinterop does not hold anything required by users
                implementation(project(":cinterop"))
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
    // FIXME MPP-BUILD We need to revisit this when we enable building on multiple hosts. Right now it doesn't do the right thing.
//    configure(listOf(targets["metadata"], jvm())) {
//        mavenPublication {
//            val targetPublication = this@mavenPublication
//            tasks.withType<AbstractPublishToMaven>()
//                .matching { it.publication == targetPublication }
//                .all { onlyIf { findProperty("isMainHost") == "true" } }
//        }
//    }
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
            isMinifyEnabled = true
            consumerProguardFiles("proguard-rules-consumer-common.pro")
        }
    }

    dependencies {
        implementation("androidx.startup:startup-runtime:${Versions.androidxStartup}")
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
        create("darwinCommon") {
            dependsOn(getByName("commonMain"))
        }
    }
}

// IOS Configuration
kotlin {
    // For ARM, should be changed to iosArm32 or iosArm64
    // For Linux, should be changed to e.g. linuxX64
    // For MacOS, should be changed to e.g. macosX64
    // For Windows, should be changed to e.g. mingwX64
    ios()
    sourceSets {
        getByName("iosMain") {
            dependsOn(getByName("darwinCommon"))
        }
        getByName("iosX64Main") {
            dependsOn(getByName("iosMain"))
        }
        getByName("iosArm64Main") {
            dependsOn(getByName("iosMain"))
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
            dependsOn(getByName("darwinCommon"))
        }
        getByName("macosTest") {
        }
    }
}

// Needs running emulator
// tasks.named("iosTest") {
//    val device: String = project.findProperty("iosDevice")?.toString() ?: "iPhone 11 Pro Max"
//    dependsOn(kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").linkTaskName)
//    group = JavaBasePlugin.VERIFICATION_GROUP
//    description = "Runs tests for target 'ios' on an iOS simulator"
//
//    doLast {
//        val binary = kotlin.targets.getByName<KotlinNativeTargetWithSimulatorTests>("ios").binaries.getTest("DEBUG").outputFile
//        exec {
//            commandLine("xcrun", "simctl", "spawn", device, binary.absolutePath)
//        }
//    }
// }

realmPublish {
    pom {
        name = "Library"
        description = "Library code for Realm Kotlin. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}

tasks.dokkaHtml.configure {
    moduleName.set("Realm Kotlin SDK ${Realm.version}")
    dokkaSourceSets {
        configureEach {
            moduleVersion.set(Realm.version)
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            perPackageOption {
                matchingRegex.set("io\\.realm\\.internal\\.*")
                suppress.set(true)
            }
        }
        val commonMain by getting {
            includes.from("overview.md", "io.realm.md")
            sourceRoot("../runtime-api/src/commonMain/kotlin")
        }
    }
}

tasks.register("uploadDokka") {
    dependsOn("dokkaHtml")
    group = "Release"
    description = "Upload SDK docs to S3"
    doLast {
        val awsAccessKey = getPropertyValue(this.project, "SDK_DOCS_AWS_ACCESS_KEY")
        val awsSecretKey = getPropertyValue(this.project, "SDK_DOCS_AWS_SECRET_KEY")

        // Failsafe check, ensuring that we catch if the path ever changes, which it might since it is an
        // implementation detail of the Kotlin Gradle Plugin
        val dokkaDir = File("$rootDir/library/build/dokka/html")
        if (!dokkaDir.exists() || !dokkaDir.isDirectory || dokkaDir.listFiles().isEmpty()) {
            throw GradleException("Could not locate dir with dokka files in: ${dokkaDir.path}")
        }

        // Upload two copies, to 'latest' and a versioned folder for posterity.
        // Symlinks would have been safer and faster, but this is not supported by S3.
        listOf(Realm.version, "latest").forEach { version: String ->
            exec {
                commandLine = listOf(
                    "s3cmd",
                    "put",
                    "--recursive",
                    "--acl-public",
                    "--access_key=$awsAccessKey",
                    "--secret_key=$awsSecretKey",
                    "${dokkaDir.absolutePath}/", // Add / to only upload content of the folder, not the folder itself.
                    "s3://realm-sdks/realm-sdks/kotlin/$version/"
                )
            }
        }
    }
}

tasks.register("dokkaJar", Jar::class) {
    dependsOn("dokkaHtml")
    archiveClassifier.set("dokka")
    from(tasks.named("dokkaHtml").get().outputs)
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    // See https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
    publications.withType<MavenPublication> {
        // Stub javadoc.jar artifact
        artifact(javadocJar.get())
    }

    val common = publications.getByName("kotlinMultiplatform") as MavenPublication
    // Configuration through examples/kmm-sample does not work if we do not resolve the tasks
    // completely, hence the .get() below.
    common.artifact(tasks.named("dokkaJar").get())
}
