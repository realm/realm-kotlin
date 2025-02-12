import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.Companion.fromTarget

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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    }
}

// Find property in either System environment or Gradle properties.
// If set in both places, Gradle properties win.
fun getPropertyValue(propertyName: String, throwIfNotFound: Boolean = false): String {
    val value: String? = (project.findProperty(propertyName) ?: System.getenv(propertyName)) as String?
    if (throwIfNotFound && (value == null || value.trim().isEmpty())) {
        throw GradleException("Could not find '$propertyName'. " +
                "Most be provided as either environment variable or " +
                "a Gradle property.")
    }
    return value ?: ""
}

// Cache dir for build artifacts that should be stored on S3
val releaseMetaDataDir = File("${buildDir}/outputs/s3")
releaseMetaDataDir.mkdirs()

fun readAndCacheVersion(): String {
    val constants: String = File("${projectDir.absolutePath}/buildSrc/src/main/kotlin/Config.kt").readText();
    val regex = "const val version = \"(.*?)\"".toRegex()
    val match: MatchResult = regex.find(constants) ?: throw GradleException("Could not find current Realm version")
    val version: String = match.groups[1]!!.value
    val versionFile = File(releaseMetaDataDir, "version.txt")
    versionFile.createNewFile()
    versionFile.writeText(version)
    return version
}
val currentVersion = readAndCacheVersion()
val subprojects = listOf("packages", "examples/kmm-sample", "benchmarks")
fun taskName(subdir: String): String {
    return subdir.split("/", "-").map { it.capitalize() }.joinToString(separator = "")
}

fun copyProperties(action: GradleBuild) {
    val propsToCopy = listOf("signBuild", "signPassword", "signSecretRingFileKotlin", "ossrhUsername", "ossrhPassword")
    val project: Project = action.project
    val buildProperties = action.startParameter.projectProperties
    propsToCopy.forEach {
        if (project.hasProperty(it)) {
            buildProperties[it] = project.property(it) as String
        }
    }
}

//allprojects {
//    version = Realm.version
//    group = Realm.group
//
//    // Define JVM bytecode target for all Kotlin targets
//    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
//        compilerOptions {
//            jvmTarget.set(fromTarget(Versions.kotlinJvmTarget))
//        }
//    }
//}

tasks {
    register("ktlintCheck") {
        description = "Runs ktlintCheck on all projects."
        group = "Verification"
        dependsOn(subprojects.map { "ktlintCheck${taskName(it)}" })
    }

    register("ktlintFormat") {
        description = "Runs ktlintFormat on all projects."
        group = "Formatting"
        dependsOn(subprojects.map { "ktlintFormat${taskName(it)}" })
    }

    register("detekt") {
        description = "Runs detekt on all projects."
        group = "Verification"
        dependsOn(subprojects.map { "detekt${taskName(it)}" })
    }

    subprojects.forEach { subdir ->
        register<Exec>("ktlintCheck${taskName(subdir)}") {
            description = "Run ktlintCheck on /$subdir project"
            workingDir = file("${rootDir}/$subdir")
            commandLine = listOf("./gradlew", "ktlintCheck")
        }
        register<Exec>("ktlintFormat${taskName(subdir)}") {
            description = "Run ktlintFormat on /$subdir project"
            workingDir = file("${rootDir}/$subdir")
            commandLine = listOf("./gradlew", "ktlintFormat")
        }
        register<Exec>("detekt${taskName(subdir)}") {
            description = "Run detekt on /$subdir project"
            workingDir = file("${rootDir}/$subdir")
            commandLine = listOf("./gradlew", "detekt")
        }
    }

    register<GradleBuild>("mavenCentralUpload") {
        description = "Push all Realm artifacts to Maven Central"
        group = "Publishing"
        buildFile = file("${rootDir}/packages/build.gradle.kts")
        tasks = listOf("publishToSonatype")
        copyProperties(this)
    }

    // TODO Verify we can actually use these debug symbols
    val archiveDebugSymbols by register("archiveDebugSymbols", Zip::class) {
//        archiveName = "realm-kotlin-jni-libs-unstripped-${currentVersion}.zip"
//        destinationDir = releaseMetaDataDir
        from("${rootDir}/packages/cinterop/build/intermediates/merged_native_libs/release/out/lib") {
            include("**/*.so")
        }
        doLast {
            // Failsafe check, ensuring that we catch if the path ever changes, which it might since it is an
            // implementation detail of the Android Gradle Plugin
            val unstrippedDir = File("${rootDir}/packages/cinterop/build/intermediates/merged_native_libs/release/out/lib")
            if (!unstrippedDir.exists() || !unstrippedDir.isDirectory || unstrippedDir.listFiles().isEmpty()) {
                throw GradleException("Could not locate unstripped binary files in: ${unstrippedDir.path}")
            }
        }
    }

    // Make sure that these parameters are set before calling tasks that uses them.
    // In Groovy we could have used an lazy evaluated GString instead, but this does
    // seem possible in Kotlin.
    val verifyS3Access by register("verifyS3Access", Task::class) {
        doLast {
            getPropertyValue("REALM_S3_ACCESS_KEY", true)
            getPropertyValue("REALM_S3_SECRET_KEY", true)
        }
    }

    val uploadDebugSymbols by register("uploadDebugSymbols", Task::class) {
        dependsOn.addAll(listOf(archiveDebugSymbols, verifyS3Access))
        doLast {
            exec {
                val s3AccessKey = getPropertyValue("REALM_S3_ACCESS_KEY")
                val s3SecretKey = getPropertyValue("REALM_S3_SECRET_KEY")
                workingDir = File("${buildDir}/outputs/s3/")
                commandLine = listOf(
                        "s3cmd",
                        "--access_key=${s3AccessKey}",
                        "--secret_key=${s3SecretKey}",
                        "put",
                        "realm-kotlin-jni-libs-unstripped-${currentVersion}.zip",
                        "s3://static.realm.io/downloads/kotlin/"
                )
            }
        }
    }

    val updateS3VersionFile by register("updateS3VersionFile", Exec::class) {
        dependsOn.add(verifyS3Access)
        val s3AccessKey = getPropertyValue("REALM_S3_ACCESS_KEY")
        val s3SecretKey = getPropertyValue("REALM_S3_SECRET_KEY")
        File("$buildDir/outputs/s3", "version.txt").writeText(currentVersion)
        commandLine = listOf(
                "s3cmd",
                "--access_key=${s3AccessKey}",
                "--secret_key=${s3SecretKey}",
                "put",
                "${buildDir}/outputs/s3/version.txt",
                "s3://static.realm.io/update/kotlin")
    }

    register<Task>("uploadReleaseMetaData") {
        group = "Release"
        description = "Upload release metadata to S3 (Native debug symbols, version files)"
        dependsOn.addAll(listOf(uploadDebugSymbols, updateS3VersionFile))
    }

     register<Task>("publishCIPackages") {
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
             when (target) {
                 "iosArm64" -> {
                     dependsOn(
                         ":packages:cinterop:publishIosArm64PublicationToTestRepository",
                         ":packages:cinterop:publishIosSimulatorArm64PublicationToTestRepository",
                         ":packages:library-base:publishIosArm64PublicationToTestRepository",
                         ":packages:library-base:publishIosSimulatorArm64PublicationToTestRepository",
                         ":packages:library-sync:publishIosArm64PublicationToTestRepository",
                         ":packages:library-sync:publishIosSimulatorArm64PublicationToTestRepository",
                     )
                 }
                 "iosX64" -> {
                     dependsOn(
                         ":packages:cinterop:publishIosX64PublicationToTestRepository",
                         ":packages:library-base:publishIosX64PublicationToTestRepository",
                         ":packages:library-sync:publishIosX64PublicationToTestRepository",
                     )
                 }
                 "jvm" -> {
                     dependsOn(
                         ":packages:jni-swig-stub:publishAllPublicationsToTestRepository",
                         ":packages:cinterop:publishJvmPublicationToTestRepository",
                         ":packages:library-base:publishJvmPublicationToTestRepository",
                         ":packages:library-sync:publishJvmPublicationToTestRepository",
                     )
                 }
                 "macosX64" -> {
                     dependsOn(
                         ":packages:cinterop:publishMacosX64PublicationToTestRepository",
                         ":packages:library-base:publishMacosX64PublicationToTestRepository",
                         ":packages:library-sync:publishMacosX64PublicationToTestRepository",
                     )
                 }
                 "macosArm64" -> {
                     dependsOn(
                         ":packages:cinterop:publishMacosArm64PublicationToTestRepository",
                         ":packages:library-base:publishMacosArm64PublicationToTestRepository",
                         ":packages:library-sync:publishMacosArm64PublicationToTestRepository",
                     )
                 }
                 "android" -> {
                     dependsOn(
                         ":packages:jni-swig-stub:publishAllPublicationsToTestRepository",
                         ":packages:cinterop:publishAndroidReleasePublicationToTestRepository",
                         ":packages:library-base:publishAndroidReleasePublicationToTestRepository",
                         ":packages:library-sync:publishAndroidReleasePublicationToTestRepository",
                     )
                 }
                 "metadata" -> {
                     dependsOn(
                         ":packages:cinterop:publishKotlinMultiplatformPublicationToTestRepository",
                         ":packages:library-base:publishKotlinMultiplatformPublicationToTestRepository",
                         ":packages:library-sync:publishKotlinMultiplatformPublicationToTestRepository",
                     )
                 }
                 "compilerPlugin" -> {
                     dependsOn(
                         ":packages:plugin-compiler:publishAllPublicationsToTestRepository",
                         ":packages:plugin-compiler-shaded:publishAllPublicationsToTestRepository"
                     )
                 }
                 "gradlePlugin" -> {
                     dependsOn(":packages:gradle-plugin:publishAllPublicationsToTestRepository")
                 }
                 else -> {
                     throw IllegalArgumentException("Unsupported target: $target")
                 }
             }
         }
     }
}
