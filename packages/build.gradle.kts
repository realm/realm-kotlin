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
import io.realm.kotlin.getPropertyValue

plugins {
    id("com.android.library") apply false
    id("realm-lint")
    `java-gradle-plugin`
    id("realm-publisher")
    id("org.jetbrains.dokka") version Versions.dokka
}

allprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
    }
    repositories {
        mavenCentral()
    }

    version = Realm.version
    group = Realm.group

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "${Versions.jvmTarget}"
    }
}

tasks.register("publishCIPackages") {
    group = "Publishing"
    description = "Publish packages that has been configured for this CI node. See `gradle.properties`."

    // Figure out which targets are configured. This will impact which sub modules will be published
    val availableTargets = setOf(
        "iosArm64",
        // "iosSimulatorArm64",
        "iosX64",
        "jvm",
        "macos", // Is really macosX64
        "macosArm64",
        "android",
        "metadata"
    )
    val mainHostTarget: Set<String> = setOf("metadata") // "kotlinMultiplatform"

    val isMainHost: Boolean? = if (project.properties.containsKey("realm.kotlin.mainHost"))  {
        project.properties["realm.kotlin.mainHost"] == "true"
    } else {
        null
    }
    // Find user configured platforms (if any)
    val userTargets: Set<String>? = (project.properties["realm.kotlin.targets"] as String?)?.split(",")?.toSet()
    userTargets?.forEach {
        if (!availableTargets.contains(it)) {
            project.logger.error("Unknown publication: $it")
            throw IllegalArgumentException("Unknown publication: $it")
        }
    }
    // Configure which platforms publications we do want to publish
    val wantedTargets: Collection<String> = when (isMainHost) {
        true -> mainHostTarget + (userTargets ?: availableTargets)
        false -> userTargets ?: (availableTargets - mainHostTarget)
        null -> availableTargets
    }

    // FIXME: We probably don't need to publish plugin and compiler plugins for each node?
    dependsOn(":gradle-plugin:publishAllPublicationsToBuildFolderRepository")
    dependsOn(":plugin-compiler:publishAllPublicationsToBuildFolderRepository")
    dependsOn(":plugin-compiler-shaded:publishAllPublicationsToBuildFolderRepository")
    if (wantedTargets.contains("jvm") || wantedTargets.contains("android")) {
        dependsOn(":jni-swig-stub:publishAllPublicationsToBuildFolderRepository")
    }


    // TODO When/How to build this?
    dependsOn(":cinterop:publishKotlinMultiplatformPublicationToBuildFolderRepository")
    dependsOn(":library-base:publishKotlinMultiplatformPublicationToBuildFolderRepository")
    dependsOn(":library-sync:publishKotlinMultiplatformPublicationToBuildFolderRepository")

    wantedTargets.forEach { target: String ->
        when(target) {
            "iosArm64" -> {
                dependsOn(
                    ":cinterop:publishIosArm64PublicationToBuildFolderRepository",
                    ":cinterop:publishIosSimulatorArm64PublicationToBuildFolderRepository",
                    ":library-base:publishIosArm64PublicationToBuildFolderRepository",
                    ":library-base:publishIosSimulatorArm64PublicationToBuildFolderRepository",
                    ":library-sync:publishIosArm64PublicationToBuildFolderRepository",
                    // TODO Sync doesn't support arm64 until we migrate to Ktor 2.0
                    // ":library-sync:publishIosSimulatorArm64PublicationToBuildFolderRepository",
                )
            }
            "iosX64" -> {
                dependsOn(
                    ":cinterop:publishIosX64PublicationToBuildFolderRepository",
                    ":library-base:publishIosX64PublicationToBuildFolderRepository",
                    ":library-sync:publishIosX64PublicationToBuildFolderRepository",
                )
            }
            "jvm" -> {
                dependsOn(
                    ":cinterop:publishJvmPublicationToBuildFolderRepository",
                    ":library-base:publishJvmPublicationToBuildFolderRepository",
                    ":library-sync:publishJvmPublicationToBuildFolderRepository",
                )
            }
            "macos" -> {
                dependsOn(
                    ":cinterop:publishMacosPublicationToBuildFolderRepository",
                    ":library-base:publishMacosPublicationToBuildFolderRepository",
                    ":library-sync:publishMacosPublicationToBuildFolderRepository",
                )
            }
            "macosArm64" -> {
                dependsOn(
                    ":cinterop:publishMacosArm64PublicationToBuildFolderRepository",
                    ":library-base:publishMacosArm64PublicationToBuildFolderRepository",
                    // TODO Sync doesn't support arm64 until we migrate to Ktor 2.0
                    // ":library-sync:publishMacosArm64PublicationToBuildFolderRepository",
                )
            }
            "android" -> {
                dependsOn(
                    ":cinterop:publishAndroidDebugPublicationToBuildFolderRepository",
                    ":cinterop:publishAndroidReleasePublicationToBuildFolderRepository",
                    ":library-base:publishAndroidDebugPublicationToBuildFolderRepository",
                    ":library-base:publishAndroidReleasePublicationToBuildFolderRepository",
                    ":library-sync:publishAndroidDebugPublicationToBuildFolderRepository",
                    ":library-sync:publishAndroidReleasePublicationToBuildFolderRepository",
                )
            }
            "metadata" -> {
                dependsOn(
                    ":cinterop:publishKotlinMultiplatformPublicationToBuildFolderRepository",
                    ":library-base:publishKotlinMultiplatformPublicationToBuildFolderRepository",
                    ":library-sync:publishKotlinMultiplatformPublicationToBuildFolderRepository",
                )
            }
            else -> {
                throw IllegalArgumentException("Unsupported target: $target")
            }
        }
    }
}

tasks.register("uploadDokka") {
    dependsOn("dokkaHtmlMultiModule")
    group = "Release"
    description = "Upload SDK docs to S3"
    doLast {
        val awsAccessKey = getPropertyValue(this.project, "SDK_DOCS_AWS_ACCESS_KEY")
        val awsSecretKey = getPropertyValue(this.project, "SDK_DOCS_AWS_SECRET_KEY")

        // Failsafe check, ensuring that we catch if the path ever changes, which it might since it is an
        // implementation detail of the Kotlin Gradle Plugin
        val dokkaDir = File("$rootDir/build/dokka/htmlMultiModule/")
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
                    "s3://realm-sdks/docs/realm-sdks/kotlin/$version/"
                )
            }
        }
    }
}
