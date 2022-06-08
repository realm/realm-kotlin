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
    id("org.jetbrains.dokka")
}

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:atomicfu-gradle-plugin:${Versions.atomicfu}")
    }
}
apply(plugin = "kotlinx-atomicfu")
// AtomicFu cannot transform JVM code. Maybe an issue with using IR backend. Throws
// ClassCastException: org.objectweb.asm.tree.InsnList cannot be cast to java.lang.Iterable
project.extensions.configure(kotlinx.atomicfu.plugin.gradle.AtomicFUPluginExtension::class) {
    transformJvm = false
}

repositories {
    google()
    mavenCentral()
    mavenLocal()
}

// Common Kotlin configuration
kotlin {
    jvm()
    android("android") {
        publishLibraryVariants("release", "debug")
    }
    ios()
    macosX64("macos") {}
    sourceSets {
        commonMain {
            dependencies {
                api(project(":library-base"))
                implementation(kotlin("stdlib-common"))
                implementation(kotlin("reflect"))
                // If runtimeapi is merged with cinterop then we will be exposing both to the users
                // Runtime holds annotations, etc. that has to be exposed to users
                // Cinterop does not hold anything required by users
                implementation(project(":cinterop"))

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}") {
                    version { strictly(Versions.coroutines) }
                }
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.serialization}")

                implementation("io.ktor:ktor-client-core:${Versions.ktor}")
                implementation("io.ktor:ktor-client-serialization:${Versions.ktor}")
                implementation("io.ktor:ktor-client-logging:${Versions.ktor}")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        create("jvm") {
            dependsOn(getByName("commonMain"))
            kotlin.srcDir("src/jvm/kotlin")
            dependencies {
                implementation("io.ktor:ktor-client-cio:${Versions.ktor}")
            }
        }
        getByName("jvmMain") {
            dependsOn(getByName("jvm"))
        }
        getByName("androidMain") {
            dependsOn(getByName("jvm"))
            dependencies {
                api(project(":cinterop"))
                implementation("androidx.startup:startup-runtime:${Versions.androidxStartup}")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
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
        getByName("macosMain") {
            // TODO HMPP Should be shared source set
            kotlin.srcDir("src/darwin/kotlin")

            // Observe this ktor dependency cannot be abstracted away with the ios ones
            dependencies {
                implementation("io.ktor:ktor-client-curl:${Versions.ktor}")
            }
        }
        getByName("iosArm64Main") {
            // TODO HMPP Should be shared source set
            kotlin.srcDir("src/darwin/kotlin")
            kotlin.srcDir("src/ios/kotlin")

            // FIXME move to shared ios source set
            dependencies {
                implementation("io.ktor:ktor-client-ios:${Versions.ktor}")
            }
        }
        getByName("iosX64Main") {
            // TODO HMPP Should be shared source set
            kotlin.srcDir("src/darwin/kotlin")
            kotlin.srcDir("src/ios/kotlin")

            // FIXME move to shared ios source set
            dependencies {
                implementation("io.ktor:ktor-client-ios:${Versions.ktor}")
            }
        }
    }

    // Require that all methods in the API have visibility modifiers and return types.
    // Anything inside `io.realm.kotlin.internal.*` is considered internal regardless of their
    // visibility modifier and will be stripped from Dokka, but will unfortunately still
    // leak into auto-complete in the IDE.
    explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Strict
}

// Using a custom name module for internal methods to avoid default name mangling in Kotlin compiler which uses the module
// name and build type variant as a suffix, this default behaviour can cause mismatch at runtime https://github.com/realm/realm-kotlin/issues/621
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        freeCompilerArgs = listOf("-module-name", "io.realm.kotlin.library")
    }
}

// Android configuration
android {
    compileSdkVersion(Versions.Android.compileSdkVersion)
    buildToolsVersion = Versions.Android.buildToolsVersion

    defaultConfig {
        minSdkVersion(Versions.Android.minSdk)
        targetSdkVersion(Versions.Android.targetSdk)
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
        ndk {
            abiFilters += setOf("x86_64", "arm64-v8a")
        }
    }

    buildTypes {
        getByName("release") {
            consumerProguardFiles("proguard-rules-consumer-common.pro")
        }
    }
    // To avoid
    // Failed to transform kotlinx-coroutines-core-jvm-1.5.0-native-mt.jar ...
    // The dependency contains Java 8 bytecode. Please enable desugaring by adding the following to build.gradle
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    // Skip BuildConfig generation as it overlaps with io.realm.kotlin.BuildConfig from realm-java
    buildFeatures {
        buildConfig = false
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
        name = "Sync Library"
        description = "Sync Library code for Realm Kotlin. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
    moduleName.set("Realm Kotlin SDK - Sync")
    moduleVersion.set(Realm.version)
    dokkaSourceSets {
        configureEach {
            moduleVersion.set(Realm.version)
            reportUndocumented.set(true)
            skipEmptyPackages.set(true)
            perPackageOption {
                matchingRegex.set(""".*\.internal.*""")
                suppress.set(true)
            }
            jdkVersion.set(8)
            includes.from("overview.md")
        }
    }
}

// tasks.register("dokkaJar", Jar::class) {
//     val dokkaTask = "dokkaHtmlPartial"
//     dependsOn(dokkaTask)
//     archiveClassifier.set("dokka")
//     from(tasks.named(dokkaTask).get().outputs)
// }

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

// FIXME: This is currently causing a full build of native sources in cinterop.
// publishing {
//     // See https://dev.to/kotlin/how-to-build-and-publish-a-kotlin-multiplatform-library-going-public-4a8k
//     publications.withType<MavenPublication> {
//         // Stub javadoc.jar artifact
//         artifact(javadocJar.get())
//     }
//
//     // TODO: configure DOKKA so that it's only published for sync and not base
//     val common = publications.getByName("kotlinMultiplatform") as MavenPublication
// //    // Configuration through examples/kmm-sample does not work if we do not resolve the tasks
// //    // completely, hence the .get() below.
//     common.artifact(tasks.named("dokkaJar").get())
// }
