/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.gradle.analytics

import io.realm.kotlin.gradle.RealmCompilerSubplugin
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.URL
import java.nio.charset.Charset
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Scanner
import javax.inject.Inject
import javax.xml.bind.DatatypeConverter
import kotlin.experimental.and

/**
 * Analytics Build Service holding cross-target project specific info and methods for dispatching
 * analytics.
 * Build Services are only marked stable from Gradle 7.4, so additional logging has been added
 * to this class to catch catches where types are different than expected. We do _NOT_
 * want analytics to take down a users build, so exceptions are avoided on purpose.
 *
 * Build Services was added in Gradle 6.1. They can be called by multiple tasks, so must
 * be implemented to be thread-safe
 *
 * **See:** [Build Services](https://docs.gradle.org/current/userguide/build_services.html)
 */

// Asynchronously submits build information to Realm when the gradle compile task run
//
// To be clear: this does *not* run when your app is in production or on
// your end-user's devices; it will only run when you build your app from source.
//
// Why are we doing this? Because it helps us build a better product for you.
// None of the data personally identifies you, your employer or your app, but it
// *will* help us understand what Realm version you use, what host OS you use,
// etc. Having this info will help with prioritizing our time, adding new
// features and deprecating old features. Collecting an anonymized bundle &
// anonymized MAC is the only way for us to count actual usage of the other
// metrics accurately. If we don't have a way to deduplicate the info reported,
// it will be useless, as a single developer building their app on Windows ten
// times would report 10 times more than a single developer that only builds
// once from Mac OS X, making the data all but useless. No one likes sharing
// data unless it's necessary, we get it, and we've debated adding this for a
// long long time. Since Realm is a free product without an email signup, we
// feel this is a necessary step so we can collect relevant data to build a
// better product for you.
//
// Currently the following information is reported:
// - What version of Realm is being used
// - What host you are running on
// - What targets you are building for
// - An anonymized MAC address and bundle ID to aggregate the other information on.
//
// The collected information will be printed as info messages to the Gradle logger named
// [realm-analytics] if settings the system environment variable
//   REALM_PRINT_ANALYTICS=true
// Collection and submission of data can be fully disabled by setting the system environment variable
//   REALM_DISABLE_ANALYTICS=true

private const val TOKEN = "ce0fac19508f6c8f20066d345d360fd0"
private const val EVENT_NAME = "Run"
private const val URL_PREFIX = "https://data.mongodb-api.com/app/realmsdkmetrics-zmhtm/endpoint/metric_webhook/metric?data="

// Container for the project specific details, thus equal across all platforms.
interface ProjectConfiguration : BuildServiceParameters {
    val appId: Property<String>
    val userId: Property<String>
    val builderId: Property<String>
    val hostOsType: Property<String>
    val hostOsVersion: Property<String>
    val hostCpuArch: Property<String>
    val usesSync: Property<Boolean>
    val verbose: Property<Boolean>
}

// Container object for target specific details that varies across compilation targets.
data class TargetInfo(
    val targetOsType: String,
    val targetCpuArch: String,
    val targetOSVersion: String?,
    val targetOSMinVersion: String?,
)

abstract class AnalyticsService : BuildService<ProjectConfiguration> {
    private val logger: Logger = Logging.getLogger("realm-analytics")

    fun submit(targetInfo: TargetInfo) {
        val projectInfo = parameters
        val json = """
            {
                "event": "$EVENT_NAME",
                    "properties": {
                    "token": "$TOKEN",
                    "distinct_id": "${projectInfo.userId.get()}",
                    "builder_id": "${projectInfo.builderId.get()}",
                    "Anonymized Bundle ID": "${projectInfo.appId.get()}",
                    "Binding": "kotlin",
                    "Language": "kotlin",
                    "Host OS Type": "${projectInfo.hostOsType.get()}",
                    "Host OS Version": "${projectInfo.hostOsVersion.get()}",
                    "Host CPU Arch": "${projectInfo.hostCpuArch.get()}",
                    "Target CPU Arch": "${targetInfo.targetCpuArch}",
                    "Target OS Type": "${targetInfo.targetOsType}",
                    "Target OS Minimum Version": "${targetInfo.targetOSMinVersion}",
                    "Target OS Version": "${targetInfo.targetOSVersion}",
                    "Realm Version": "${RealmCompilerSubplugin.version}",
                    "Core Version": "${RealmCompilerSubplugin.coreVersion}",
                    "Sync Enabled": ${if (projectInfo.usesSync.get()) "true" else "false"}
                }
            }
        """.trimIndent().replace("\n", "").replace("    ", "")
        sendAnalytics(json, projectInfo.verbose.get())
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendAnalytics(json: String, verbose: Boolean) {
        try {
            if (!verbose) {
                debug("Submitting analytics payload: $json")
            } else {
                info("Submitting analytics payload: $json")
            }
            Thread {
                try {
                    val url = URL(URL_PREFIX + base64Encode(json))
                    val connection = url.openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connect()
                    debug("Analytics payload sent")
                } catch (e: InterruptedException) {
                    debug("Submitting analytics was interrupted")
                } catch (e: Throwable) {
                    debug("Error submitting analytics: ${e.message}")
                }
            }.apply {
                isDaemon = true
            }.start()
        } catch (e: Exception) {
            // Analytics failing for any reason should not crash the build
            debug("Submitting analytics payload failed: $e")
        }
    }

    private fun debug(message: String) = logger.debug(message)
    private fun info(message: String) = logger.info(message)
}

// Common abstraction to collection various host identification identifiers through various exec
// operations
abstract class HostIdentifier : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject
    abstract val execOperations: ExecOperations

    val identifier: String =
        when (HOST_OS) {
            Host.WINDOWS -> windowsIdentifier
            Host.MACOS -> macOsIdentifier
            Host.LINUX -> linuxIdentifier
            else -> throw IllegalStateException("Unknown host identifier")
        }
    abstract val linuxIdentifier: String
    abstract val macOsIdentifier: String
    abstract val windowsIdentifier: String

    companion object {
        const val UNKNOWN = "unknown"
    }
}

/**
 * Provider of a unique identifier for a computer. The method being used depends on the platform:
 *  - OS X:  Mac address of en0
 *  - Windows:  BIOS identifier
 *  - Linux: Machine ID provided by the OS
 */
abstract class ComputerId : HostIdentifier() {
    override val linuxIdentifier: String
        get() {
            var machineId = File("/var/lib/dbus/machine-id")
            if (!machineId.exists()) {
                machineId = File("/etc/machine-id")
            }
            if (!machineId.exists()) {
                return UNKNOWN
            }
            var scanner: Scanner? = null
            return try {
                scanner = Scanner(machineId)
                val id = scanner.useDelimiter("\\A").next()
                id
            } finally {
                scanner?.close()
            }
        }

    override val macOsIdentifier: String
        get() {
            val networkInterface = NetworkInterface.getByName("en0")
            val hardwareAddress = networkInterface.hardwareAddress
            return String(hardwareAddress, Charset.defaultCharset())
        }

    override val windowsIdentifier: String
        get() {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                it.commandLine("wmic", "csproduct", "get", "UUID")
                it.standardOutput = output
            }
            val input = String(output.toByteArray(), Charset.defaultCharset())
            var result: String? = null
            val sc = Scanner(input)
            while (sc.hasNext()) {
                val next = sc.next()
                if (next.contains("UUID")) {
                    result = sc.next().trim { it <= ' ' }
                    break
                }
            }
            return result!!
        }

    override fun obtain(): String? = try {
        hexStringify(sha256Hash(identifier.toByteArray()))
    } catch (e: Exception) {
        UNKNOWN
    }
}

/**
 * Provider of a unique builder identifier for a computer.
 *
 * Successor of [ComputerId] standardized across SDKs.
 */
abstract class BuilderId : HostIdentifier() {
    override val linuxIdentifier: String
        get() {
            return File("/etc/machine-id").inputStream().readBytes().toString().trim()
        }

    override val macOsIdentifier: String
        get() {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                it.commandLine("ioreg", "-rd1", "-c", "IOPlatformExpertDevice")
                it.standardOutput = output
            }
            val input = String(output.toByteArray(), Charset.defaultCharset())
            val regEx = ".*\"IOPlatformUUID\"\\s=\\s\"(.+)\"".toRegex()
            val find: MatchResult? = regEx.find(input)
            return find?.groups?.get(1)?.value!!
        }

    override val windowsIdentifier: String
        get() {
            val output = ByteArrayOutputStream()
            execOperations.exec {
                it.commandLine("Reg", "QUERY", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "MachineGuid")
                it.standardOutput = output
            }
            val input = String(output.toByteArray(), Charset.defaultCharset())
            // Manually expanded [:alnum:] as ([[:alnum:]-]+) didn't seems to work
            val regEx = "\\s*MachineGuid\\s*\\w*\\s*([A-Za-z0-9-]+)".toRegex()
            val find: MatchResult? = regEx.find(input)
            return find?.groups?.get(1)?.value!!
        }

    override fun obtain(): String = try {
        val id = identifier
        val data = "Realm is great$id"
        base64Encode(sha256Hash(data.toByteArray()))!!
    } catch (e: Exception) {
        "UNKNOWN"
    }
}

/**
 * Encode the given string with Base64
 * @param data the string to encode
 * @return the encoded string
 * @throws UnsupportedEncodingException
 */
@Throws(UnsupportedEncodingException::class)
internal fun base64Encode(data: String): String? {
    return base64Encode(data.toByteArray(charset("UTF-8")))
}

internal fun base64Encode(data: ByteArray): String? {
    return DatatypeConverter.printBase64Binary(data)
}

/**
 * Compute the SHA-256 hash of the given byte array
 * @param data the byte array to hash
 * @return the hashed byte array
 * @throws NoSuchAlgorithmException
 */
@Throws(NoSuchAlgorithmException::class)
internal fun sha256Hash(data: ByteArray?): ByteArray {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    return messageDigest.digest(data)
}

/**
 * Convert a byte array to its hex-string
 * @param data the byte array to convert
 * @return the hex-string of the byte array
 */
@Suppress("MagicNumber")
internal fun hexStringify(data: ByteArray): String {
    val stringBuilder = java.lang.StringBuilder()
    for (singleByte: Byte in data) {
        stringBuilder.append(((singleByte and 0xff.toByte()) + 0x100).toString(16).substring(1))
    }
    return stringBuilder.toString()
}

enum class Host(val serializedName: String) {
    WINDOWS("Windows"), LINUX("Linux"), MACOS("macOs"), UNKNOWN("Unknown");
}

/**
 * Define which Host OS the build is running on.
 */
val HOST_OS: Host = run {
    val hostOs = System.getProperty("os.name")
    when {
        hostOs.contains("windows", ignoreCase = true) -> Host.WINDOWS
        hostOs.contains("inux", ignoreCase = true) -> Host.LINUX
        hostOs.contains("mac", ignoreCase = true) -> Host.MACOS
        else -> Host.UNKNOWN
    }
}

enum class Architecture(val serializedName: String) {
    X86("x86"),
    X64("x64"),
    ARM("Arm"),
    ARM64("Arm64"),
}

/**
 * String that represents the architecture of the host the build is running on.
 */
val HOST_ARCH: String = run {
    val hostArch = System.getProperty("os.arch")
    when {
        hostArch.contains("x86") && hostArch.contains("64") -> Architecture.X64.serializedName
        hostArch.contains("x86") -> Architecture.X64.serializedName
        hostArch.contains("aarch") && hostArch.contains("64") -> Architecture.ARM64.serializedName
        hostArch.contains("aarch") -> Architecture.ARM.serializedName
        else -> "Unknown[$hostArch]"
    }
}
