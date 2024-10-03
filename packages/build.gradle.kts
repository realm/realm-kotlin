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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.library") apply false
    id("realm-lint")
    `java-gradle-plugin`
    id("realm-publisher")
    id("org.jetbrains.dokka") version Versions.dokka
}

allprojects {
    version = Realm.version
    group = Realm.group

    // Define JVM bytecode target for all Kotlin targets
    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(Versions.kotlinJvmTarget))
        }
    }
}

/**
 * Task that will build and publish the defined packages to <root>/packages/build/m2-buildrepo`.
 * This is mostly suited for CI jobs that wants to build select publications on specific runners.
 *
 *
 * See `gradle.properties` for specific configuration options available to this task.
 *
 * For local development, using:
 *
 * ```
 * > ./gradlew publishAllPublicationsToTestRepository
 * ```
 *
 * will build and publish all targets available to the builder platform.
 */
tasks.register("publishCIPackages") {
    group = "Publishing"
    description = "Publish packages that has been configured for this CI node. See `gradle.properties`."

    // Figure out which targets are configured. This will impact which sub modules will be published
    val availableTargets = setOf(
        "iosArm64",
        "iosX64",
        "jvm",
        "macosX64",
        "macosArm64",
        "android",
        "metadata",
        "compilerPlugin",
        "gradlePlugin"
    )

    val mainHostTarget: Set<String> = setOf("metadata") // "kotlinMultiplatform"

    val isMainHost: Boolean = project.properties["realm.kotlin.mainHost"]?.let { it == "true" } ?: false

    // Find user configured platforms (if any)
    val userTargets: Set<String>? = (project.properties["realm.kotlin.targets"] as String?)
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.toSet()

    userTargets?.forEach {
        if (!availableTargets.contains(it)) {
            project.logger.error("Unknown publication: $it")
            throw IllegalArgumentException("Unknown publication: $it")
        }
    }

    // Configure which platforms publications we do want to publish
    val publicationTargets = (userTargets ?: availableTargets).let {
        when (isMainHost) {
            true -> it + mainHostTarget
            false -> it - mainHostTarget
        }
    }

    publicationTargets.forEach { target: String ->
        when(target) {
            "iosArm64" -> {
                dependsOn(
                    ":cinterop:publishIosArm64PublicationToTestRepository",
                    ":cinterop:publishIosSimulatorArm64PublicationToTestRepository",
                    ":library-base:publishIosArm64PublicationToTestRepository",
                    ":library-base:publishIosSimulatorArm64PublicationToTestRepository",
                )
            }
            "iosX64" -> {
                dependsOn(
                    ":cinterop:publishIosX64PublicationToTestRepository",
                    ":library-base:publishIosX64PublicationToTestRepository",
                )
            }
            "jvm" -> {
                dependsOn(
                    ":jni-swig-stub:publishAllPublicationsToTestRepository",
                    ":cinterop:publishJvmPublicationToTestRepository",
                    ":library-base:publishJvmPublicationToTestRepository",
                )
            }
            "macosX64" -> {
                dependsOn(
                    ":cinterop:publishMacosX64PublicationToTestRepository",
                    ":library-base:publishMacosX64PublicationToTestRepository",
                )
            }
            "macosArm64" -> {
                dependsOn(
                    ":cinterop:publishMacosArm64PublicationToTestRepository",
                    ":library-base:publishMacosArm64PublicationToTestRepository",
                )
            }
            "android" -> {
                dependsOn(
                    ":jni-swig-stub:publishAllPublicationsToTestRepository",
                    ":cinterop:publishAndroidReleasePublicationToTestRepository",
                    ":library-base:publishAndroidReleasePublicationToTestRepository",
                )
            }
            "metadata" -> {
                dependsOn(
                    ":cinterop:publishKotlinMultiplatformPublicationToTestRepository",
                    ":library-base:publishKotlinMultiplatformPublicationToTestRepository",
                )
            }
            "compilerPlugin" -> {
                dependsOn(
                    ":plugin-compiler:publishAllPublicationsToTestRepository",
                    ":plugin-compiler-shaded:publishAllPublicationsToTestRepository"
                )
            }
            "gradlePlugin" -> {
                dependsOn(":gradle-plugin:publishAllPublicationsToTestRepository")
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
                    "--no-mime-magic",
                    "--guess-mime-type",
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
