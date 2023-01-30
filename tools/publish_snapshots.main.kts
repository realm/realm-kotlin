@file:Repository("https://maven.pkg.jetbrains.space/public/p/kotlinx-html/maven")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.3")
@file:ScriptFileLocation("scriptPath")

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * Script that will take a local maven repo and clone it to Maven Central.
 * The script supports both normal and SNAPSHOT releases.
 *
 * This makes it possible to create a full maven repository across multiple
 * GitHub Action runners and then finally upload all artifacts in one go once
 * all tests have passed.
 */
// Wrapper describing a single file in a package + including metadata needed when uploading to Maven Central.
data class FileDescriptor(val fileName: String, val classifier: String, val type: String)
// Wrapper for data required to upload a single package to Maven Central.
data class PackageData(
    val fullPathToPackage: String,
    val pomFile: FileDescriptor,
    val mainFile: FileDescriptor,
    val additionalFiles: List<FileDescriptor>)

fun debug(message: String?) {
    println(message)
}

if (args.size != 6) {
    println("Usage: kotlin publish_snapshots.main.kts <path_to_kotlin_repo> <version> <gpg_signing_key> <gpg_pass_phrase> <maven_central_username> <maven_central_password>")
    exitProcess(1)
}

// Constants
// Only files of this type are candidates to be the _main_ file
val mainFileTypes = listOf("aar", "jar", "klib")
// Files with this type is ignored when determining which files to upload
val ignoredFileTypes = listOf("md5", "sha1", "sha256", "sha512", "asc")
// Files that match this name is ignored when determining which files to upload
val ignoreFiles = listOf("maven-metadata.xml")
// Full path to the root of the realm-kotlin repo
val repoPath: String = File(args[0]).absolutePath
// Version of the SDK to upload, this can both be a full release or a -SNAPSHOT release
val version = args[1]
// SNAPSHOT releases are only marked as such in the folder structure, files inside contain only the version without
// the -SNAPSHOT suffix.
val versionPrefix = version.removeSuffix("-SNAPSHOT")
// Wether or not we are about to upload a SNAPSHOT release.
val isSnapshot = version.endsWith("-SNAPSHOT", ignoreCase = true)
// Secret key used to sign artifacts. Must be encoded using base64.
// The following code can be used to create this:
// # Export base64 key
// gpg --export-secret-key --armor | base64
// # Import base64 key with a passphrase
// echo $BASE64_SIGNING_KEY | base64 -d | gpg --batch --import
val gpgBase64SigningKey = args[2]
val gpgPassPhrase = args[3]
// Maven Central username and password token.
// Can be found by logging in to https://oss.sonatype.org/#welcome and go to Profile > User Token.
val sonatypeUsername = args[4]
val sonatypePassword = args[5]
// Path to the root of the local m2 repo containing the package to release.
val localMavenRepo = File("$repoPath/packages/build/m2-buildrepo")
// Url to upload the release to
val mavenCentralStagingUrl="https://oss.sonatype.org/service/local/staging/deploy/maven2"
// Repository ID used in ~/.m2/settings.xml
val mavenCentralRepoId="ossrh"

debug("Setup signing key")
runCommand(listOf("/bin/sh", "-c", "echo '$gpgBase64SigningKey' | base64 -d | gpg --batch --import"))

debug("Setup Maven credentials")
val mavenSettingsDir = File(System.getProperty("user.home"), ".m2")
mavenSettingsDir.mkdir()
val settingsFile = File(mavenSettingsDir, "settings.xml")
if (!settingsFile.exists()) {
    settingsFile.createNewFile()
}
settingsFile.writeText("""
    <settings 
        xmlns="http://maven.apache.org/SETTINGS/1.0.0" 
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
        <profiles>
            <profile>
                <id>gpg</id>
                <properties>
                    <gpg.executable>gpg</gpg.executable>
                    <gpg.passphrase>$gpgPassPhrase</gpg.passphrase>
                </properties>
            </profile>
        </profiles>
        <activeProfiles>
            <activeProfile>gpg</activeProfile>
        </activeProfiles>
        <servers>
            <server>
                <id>$mavenCentralRepoId</id>
                <username>$sonatypeUsername</username>
                <password>$sonatypePassword</password>
            </server>
        </servers>
    </settings>
""".trimIndent())

debug("Upload artifacts for $version")

// Iterate through a local Maven repository and find all Realm Kotlin packages that neeeds to be uploaded.
val packages: List<String> = File(localMavenRepo, "io/realm/kotlin").listFiles()
    .filter { file -> !file.isHidden && file.isDirectory }
    .map { file -> file.name }

debug("Found the following packages:\n${packages.joinToString(separator = "") { " - $it\n" }}")
packages.forEach { packageName ->
    debug("Process package: $packageName")
    val versionDirectory = File(localMavenRepo, "io/realm/kotlin/$packageName/$version")
    if (!versionDirectory.exists()) {
        throw IllegalStateException("$versionDirectory does not exists.")
    }

    // Find pom file which _must_ exists.
    val (snapshotTimestamp: String, pomFile: File) = findPomFile(versionDirectory, "$packageName-$versionPrefix")

    // Find all files from this package that must be uploaded
    val packageData = if (isSnapshot) {
        findSnapshotFiles(versionDirectory, pomFile, "$packageName-$versionPrefix$snapshotTimestamp")
    } else {
        findReleaseFiles()
    }

    // Upload package files to Maven Central
    uploadFiles(packageData)
}

/**
 * Iterate a folder and return all files that needs to be considered as part of a Maven Artifact.
 */
fun iteratePackageFiles(directory: File): Sequence<File> {
    return directory.walkTopDown()
        .filter { it.isFile }
        .filterNot { ignoreFiles.contains(it.name) }
        .filter { file ->
            ignoredFileTypes.firstOrNull {fileType ->
                file.name.endsWith(fileType)
            } == null
        }
}

fun findPomFile(versionDirectory: File, packageAndVersionPrefix: String): Pair<String, File> {
    val pomFilePattern = "$packageAndVersionPrefix(-[0-9]{8}.[0-9]{6}-[0-9])?.pom"
    val pomFiles: List<File> = iteratePackageFiles(versionDirectory)
        .filter {it.name.matches(Regex(pomFilePattern)) }
        .toList()

    return when(pomFiles.size) {
        0 -> throw IllegalStateException("Could not find pom file matching: $pomFilePattern in ${versionDirectory.absolutePath}")
        1 -> Pair(getSnapshotTimestamp(pomFiles.first().name, packageAndVersionPrefix), pomFiles.first())
        else -> {
            val snapshots = pomFiles.map { pomFile ->
                Pair(getSnapshotTimestamp(pomFile.name, packageAndVersionPrefix), pomFile)
            }.toSet().sortedByDescending { it.first }
            debug("Found following SNAPSHOT candidates:\n${snapshots.joinToString(separator = "") {" - ${it.first}\n" }}")

            val selectedSnapshot = snapshots.first()
            debug("Use selected SNAPSHOT: ${selectedSnapshot.first}")
            return selectedSnapshot
        }
    }
}

/**
 * From the pom file we can extract the timestamp we expect to see on all other files.
 */
fun getSnapshotTimestamp(fileName: String, packageAndVersionPrefix: String): String {
    return fileName.removePrefix(packageAndVersionPrefix).removeSuffix(".pom")
}

/**
 * Find all files in a directory that is part of a SNAPSHOT release.
 */
fun findSnapshotFiles(versionDirectory: File, pomFile: File, packageAndVersionPrefix: String): PackageData {
    val files = mutableListOf<FileDescriptor>()
    iteratePackageFiles(versionDirectory).forEach { file: File ->
        // Ignore files from non-selected SNAPSHOT versions
        if (!file.name.startsWith(packageAndVersionPrefix)) {
            return@forEach
        }
        val name = file.name
        val type = name.split(".").last()
        val classifier = name
            .removePrefix(packageAndVersionPrefix)
            .let { name ->
                if (name.startsWith(".")) {
                    ""
                } else {
                    name.split(".").first().removePrefix("-")
                }
            }
        val file = FileDescriptor(name, classifier, type)
        debug("Found file: $file")
        files.add(file)
    }

    // Categorize files, most importantly find the pom and main file.
    val pomFile = files.first { it.fileName == pomFile.name }
    val mainFile: FileDescriptor = files.filter { file: FileDescriptor ->
        file.classifier.isEmpty() && mainFileTypes.contains(file.type)
    }.also { files: List<FileDescriptor> ->
        if (files.size > 1) {
            throw IllegalStateException("Multiple candidates for the main file: ${files.joinToString(", ")}")
        }
    }.first()
    val additionalFiles = files.filterNot { it == mainFile || it.fileName == pomFile.fileName }

    return PackageData(
        fullPathToPackage = versionDirectory.absolutePath,
        pomFile = pomFile,
        mainFile = mainFile,
        additionalFiles = additionalFiles
    )
}

fun findReleaseFiles(): PackageData {
    TODO("Not yet implemented")
//    iteratePackageFiles(packageDirectory).forEach {
//        // Verify that all files have the correct prefix of package name + version
//        if (!it.name.startsWith("$packageName-$versionPrefix")) {
//            throw IllegalStateException("Directory ${packageDirectory.absoluteFile} contain " +
//                    "files from multiple versions. Expected only $version, but found ${it.name}")
//        }
//    }
}

fun uploadFiles(files: PackageData) {
    val args = mutableListOf<String>()
    args.run {
        // See https://maven.apache.org/plugins/maven-gpg-plugin/sign-and-deploy-file-mojo.html
        add("mvn")
        add("gpg:sign-and-deploy-file")
        add("-Durl=https://oss.sonatype.org/content/repositories/snapshots")
        add("-DrepositoryId=ossrh")
        add("-DpomFile=${files.fullPathToPackage}/${files.pomFile.fileName}")
        add("-Dfile=${files.fullPathToPackage}/${files.mainFile.fileName}")
        add("-Dfiles=${files.additionalFiles.map { "${files.fullPathToPackage}/${it.fileName}" }.joinToString(",")}")
        add("-Dclassifiers=${files.additionalFiles.map { it.classifier }.joinToString(",")}")
        add("-Dtypes=${files.additionalFiles.map { it.type }.joinToString(",")}")
    }
    debug("Running command: ${args.joinToString(" ")}")
    runCommand(args, showOutput = true)
}

/**
 * Run a system command and collect any output.
 */
fun runCommand(args: List<String>, showOutput: Boolean = false) {
    val commands: Array<String> = args.toTypedArray()
    val proc: Process = Runtime.getRuntime().exec(commands)
    val stdInput = BufferedReader(InputStreamReader(proc.inputStream))
    val stdError = BufferedReader(InputStreamReader(proc.errorStream))
    if (showOutput) {
        debug("Standard output:")
        var s: String?
        while (stdInput.readLine().also { s = it } != null) {
            debug(s)
        }
        debug("Error output (if any):")
        while (stdError.readLine().also { s = it } != null) {
            debug(s)
        }
    }
    proc.waitFor().let { exitValue ->
        if (exitValue != 0) {
            throw IllegalStateException("Exit value: $exitValue")
        }
    }
}
