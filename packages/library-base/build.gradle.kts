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
    kotlin("plugin.serialization") version Versions.kotlin
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

// Common Kotlin configuration
kotlin {
    jvm()
    androidTarget("android") {
        // Changing this will also requires an update to the publishCIPackages task
        // in /packages/build.gradle.kts
        publishLibraryVariants("release")
    }
    ios()
    iosSimulatorArm64()
    macosX64()
    macosArm64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation(kotlin("reflect"))
                // If runtimeapi is merged with cinterop then we will be exposing both to the users
                // Runtime holds annotations, etc. that has to be exposed to users
                // Cinterop does not hold anything required by users

                // NOTE: scope needs to be API since 'implementation' will produce a POM with 'runtime' scope
                //       causing the compiler plugin to fail to lookup classes from the 'cinterop' package
                api(project(":cinterop"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("org.jetbrains.kotlinx:atomicfu:${Versions.atomicfu}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:${Versions.serialization}")
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test-common"))
                implementation(kotlin("test-annotations-common"))
            }
        }
        val jvm by creating {
            dependsOn(commonMain)
        }
        val jvmMain by getting {
            dependsOn(jvm)
        }
        val androidMain by getting {
            dependsOn(jvm)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")
            }
        }
        val nativeDarwin by creating {
            dependsOn(commonMain)
        }
        val nativeMacos by creating {
            dependsOn(nativeDarwin)
        }
        val nativeIos by creating {
            dependsOn(nativeDarwin)
        }
        val macosX64Main by getting {
            dependsOn(nativeMacos)
        }
        val macosArm64Main by getting {
            dependsOn(nativeMacos)
        }
        val iosArm64Main by getting {
            dependsOn(nativeIos)
        }
        val iosSimulatorArm64Main by getting {
            dependsOn(nativeIos)
        }
        val iosX64Main by getting {
            dependsOn(nativeIos)
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
        freeCompilerArgs += listOf("-module-name", "io.realm.kotlin.library")
    }
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().all {
    kotlinOptions {
        freeCompilerArgs += listOf("-opt-in=kotlinx.cinterop.ExperimentalForeignApi")
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
            }
        }
        ndk {
            abiFilters += setOf("x86_64", "arm64-v8a")
        }
    }

    buildTypes {
        getByName("debug") {
            consumerProguardFiles("proguard-rules-consumer-common.pro")
        }
        getByName("release") {
            consumerProguardFiles("proguard-rules-consumer-common.pro")
        }
    }
    compileOptions {
        sourceCompatibility = Versions.sourceCompatibilityVersion
        targetCompatibility = Versions.targetCompatibilityVersion
    }
    // Skip BuildConfig generation as it overlaps with io.realm.kotlin.BuildConfig from realm-java
    buildFeatures {
        buildConfig = false
    }
}

realmPublish {
    pom {
        name = "Library"
        description = "Library code for Realm Kotlin. This artifact is not " +
            "supposed to be consumed directly, but through " +
            "'io.realm.kotlin:gradle-plugin:${Realm.version}' instead."
    }
}

tasks.withType<org.jetbrains.dokka.gradle.DokkaTaskPartial>().configureEach {
    moduleName.set("Realm Kotlin SDK")
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
        }
        val commonMain by getting {
            includes.from(
                "overview.md",
                // TODO We could actually include package descriptions in top level overview file
                //  with:
                //    # package io.realm.kotlin
                //  Maybe worth a consideration
                "src/commonMain/kotlin/io/realm/kotlin/info.md",
                "src/commonMain/kotlin/io/realm/kotlin/log/info.md"
            )
            sourceRoot("../runtime-api/src/commonMain/kotlin")
        }
    }
}

 tasks.register("dokkaJar", Jar::class) {
     val dokkaTask = "dokkaHtmlPartial"
     dependsOn(dokkaTask)
     archiveClassifier.set("dokka")
     from(tasks.named(dokkaTask).get().outputs)
 }

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

// Make sure that docs are published for the Metadata publication as well. This is required
// by Maven Central
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
