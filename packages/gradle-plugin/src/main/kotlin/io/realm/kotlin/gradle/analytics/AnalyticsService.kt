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
import io.realm.kotlin.gradle.analytics.AnalyticsService.Companion.unknown
import io.realm.kotlin.gradle.gradle75
import io.realm.kotlin.gradle.gradleVersion
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.com.google.gson.GsonBuilder
import org.jetbrains.kotlin.com.google.gson.JsonObject
import org.jetbrains.kotlin.com.google.gson.JsonPrimitive
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
    val languageVersion: Property<String>
}

// Container object for target specific details that varies across compilation targets.
data class TargetInfo(
    val targetOsType: String,
    val targetCpuArch: String,
    val targetOSVersion: String?,
    val targetOSMinVersion: String?,
)

abstract class AnalyticsService : BuildService<ProjectConfiguration> {

    private val projectInfo = JsonObject()
    // FIXME Is this thread safe
    private val jsonSerializer = GsonBuilder().create()

    init {
        val parameters = parameters
        projectInfo.add("event", JsonPrimitive(EVENT_NAME))
        projectInfo.add(
            "properties",
            JsonObject().apply {
                add("token", JsonPrimitive(TOKEN))
                add("distinct_id", JsonPrimitive(parameters.userId.get()))
                add("builder_id", JsonPrimitive(parameters.builderId.get()))
                add("Anonymized Bundle ID", JsonPrimitive(parameters.appId.get()))
                add("Binding", JsonPrimitive("kotlin"))
                add("Language", JsonPrimitive("kotlin"))
                add("Host OS Type", JsonPrimitive(parameters.hostOsType.get()))
                add("Host OS Version", JsonPrimitive(parameters.hostOsVersion.get()))
                add("Host CPU Arch", JsonPrimitive(parameters.hostCpuArch.get()))
                add("Realm Version", JsonPrimitive(RealmCompilerSubplugin.version))
                add("Core Version", JsonPrimitive(RealmCompilerSubplugin.coreVersion))
                add(
                    "Sync Enabled",
                    JsonPrimitive(if (parameters.usesSync.get()) "true" else "false")
                )
                add("Language Version", JsonPrimitive(parameters.languageVersion.get()))
            }
        )
    }

    internal fun toJson(
        targetInfo: TargetInfo? = null
    ): String {
        val targetSpecificJson = projectInfo.deepCopy()
        val properties = targetSpecificJson.getAsJsonObject("properties")
        targetInfo?.targetCpuArch?.let { properties.add("Target OS Arch", JsonPrimitive(it)) }
        targetInfo?.targetOsType?.let { properties.add("Target OS Type", JsonPrimitive(it)) }
        targetInfo?.targetOSMinVersion?.let { properties.add("Target OS Minimum Version", JsonPrimitive(it)) }
        targetInfo?.targetOSVersion?.let { properties.add("Target OS Version", JsonPrimitive(it)) }
        return jsonSerializer.toJson(targetSpecificJson)
    }

    internal fun print(json: String) {
        info("[realm-analytics] Payload: $json")
    }

    @Suppress("TooGenericExceptionCaught")
    internal fun submit(json: String) {
        try {
            debug("Submitting analytics payload: $json")
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

    private fun debug(message: String) = LOGGER.debug(message)
    private fun info(message: String) = LOGGER.info(message)

    companion object {
        internal val LOGGER: Logger = Logging.getLogger("realm-analytics")
        internal const val UNKNOWN = "Unknown"
        internal fun unknown(message: String? = null) = "$UNKNOWN${message?.let { "($it)" } ?: ""}"
    }
}

interface ErrorWrapper {
    /**
     * Property controlling whether an error happening in the code `block` of [withDefaultOnError]
     * should be causing thrown or ignored and reported as the `default` value instead.
     */
    val failOnError: Boolean

    /**
     * Utility method to wrap property collection in a common pattern that either returns a default
     * value or rethrows if collection gathering throws depending on the [failOnError] property.
     */
    fun <T> withDefaultOnError(name: String, default: T, block: () -> T): T =
        when (failOnError) {
            true -> block()
            false -> try {
                block()
            } catch (e: Throwable) {
                AnalyticsService.LOGGER.debug("Error collecting '$name': ${e.message}"); default
            }
        }
}

/**
 * [HostIdentifier] parameter to control if errors should trigger default values or propagate out
 * and fail the build.
 */
interface HostIdentifierParameters : ValueSourceParameters {
    val failOnError: Property<Boolean>
}

/**
 * Abstraction of shell execution to support Gradle configuration cache and hide Gradle version
 * differentiation, especially https://github.com/gradle/gradle/issues/18213
 */
interface Executor {

    val execOperations: ExecOperations
    fun exec(args: List<String>): String {
        return when {
            // FIXME Differentiate by gradle version as earlier version does not support ExecOperation
            //  https://github.com/gradle/gradle/issues/18213
            gradleVersion < gradle75 -> {
                val runtime = Runtime.getRuntime()
                val process = runtime.exec(args.toTypedArray())
                String(process.inputStream.readBytes())
            }
            else -> {
                val output = ByteArrayOutputStream()
                execOperations.exec {
                    it.commandLine(args)
                    it.standardOutput = output
                }
                String(output.toByteArray(), Charset.defaultCharset())
            }
        }
    }
}

/**
 * Common abstraction of tasks that collects host identifiers through various exec/file operations.
 */
abstract class HostIdentifier : ValueSource<String, HostIdentifierParameters>, Executor, ErrorWrapper {

    @get:Inject
    abstract override val execOperations: ExecOperations
    override val failOnError: Boolean
        get() = parameters.failOnError.get()

    val identifier: String
        get() {
            return when (HOST_OS) {
                Host.WINDOWS -> windowsIdentifier
                Host.MACOS -> macOsIdentifier
                Host.LINUX -> linuxIdentifier
                else -> throw IllegalStateException("Unknown host identifier")
            }
        }
    abstract val linuxIdentifier: String
    abstract val macOsIdentifier: String
    abstract val windowsIdentifier: String
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
                throw IllegalStateException("Cannot locate machine identifier in ${machineId.absolutePath}")
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
            val output = exec(listOf("wmic", "csproduct", "get", "UUID"))
            val sc = Scanner(output)
            var result: String? = null
            while (sc.hasNext()) {
                val next = sc.next()
                if (next.contains("UUID")) {
                    result = sc.next().trim { it <= ' ' }
                    break
                }
            }
            return result!!
        }

    override fun obtain(): String? = withDefaultOnError("ComputerId", unknown()) {
        hexStringify(sha256Hash(identifier.toByteArray()))
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
            val output = exec(listOf("ioeg", "-rd1", "-c", "IOPlatformExpertDevice"))
            val regEx = ".*\"IOPlatformUUID\"\\s=\\s\"(.+)\"".toRegex()
            val find: MatchResult? = regEx.find(output)
            return find?.groups?.get(1)?.value!!
        }

    override val windowsIdentifier: String
        get() {
            val output = exec(listOf("reg", "QUERY", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Cryptography", "/v", "MachineGuid"))
            // Manually expanded [:alnum:] as ([[:alnum:]-]+) didn't seems to work
            // Output from Windows will be something like `MachineGuid    REG_SZ    1c197ec7-adbd-4c3a-8386-306c20e0f686`
            val regEx = "\\s*MachineGuid\\s*\\w*\\s*([A-Za-z0-9-]+)".toRegex()
            val find: MatchResult? = regEx.find(output)
            return find?.groups?.get(1)?.value!!
        }

    override fun obtain(): String = withDefaultOnError("BuilderID", unknown()) {
        val id = identifier
        val data = "Realm is great$id"
        base64Encode(sha256Hash(data.toByteArray()))!!
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
    WINDOWS("Windows"), LINUX("Linux"), MACOS("macOs");
}

/**
 * Define which Host OS the build is running on.
 */
val HOST_OS: Host
    get() {
        val hostOs = System.getProperty("os.name")
        return when {
            hostOs.contains("windows", ignoreCase = true) -> Host.WINDOWS
            hostOs.contains("inux", ignoreCase = true) -> Host.LINUX
            hostOs.contains("mac", ignoreCase = true) -> Host.MACOS
            else -> throw IllegalArgumentException(hostOs)
        }
    }

val HOST_OS_NAME: String
    get() = try {
        HOST_OS.serializedName
    } catch (e: Throwable) {
        unknown(System.getProperty("os.name"))
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
val HOST_ARCH_NAME: String
    get() = run {
        val hostArch = System.getProperty("os.arch")
        when {
            hostArch.contains("x86") && hostArch.contains("64") -> Architecture.X64.serializedName
            hostArch.contains("x86") -> Architecture.X64.serializedName
            hostArch.contains("aarch") && hostArch.contains("64") -> Architecture.ARM64.serializedName
            hostArch.contains("aarch") -> Architecture.ARM.serializedName
            else -> unknown(hostArch)
        }
    }
