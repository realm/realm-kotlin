/*
 * Copyright 2021 Realm Inc.
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

package io.realm.gradle

import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.TaskState
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.HttpURLConnection
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URL
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Scanner
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.bind.DatatypeConverter
import kotlin.experimental.and

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
// - What OS you are running on
// - An anonymized MAC address and bundle ID to aggregate the other information on.

private const val CONNECT_TIMEOUT = 4000L
private const val READ_TIMEOUT = 2000L
private const val TOKEN = "ce0fac19508f6c8f20066d345d360fd0"
private const val EVENT_NAME = "Run"
private const val URL_PREFIX = "https://webhooks.mongodb-realm.com/api/client/v2.0/app/realmsdkmetrics-zmhtm/service/metric_webhook/incoming_webhook/metric?data="

internal class RealmAnalytics : TaskExecutionAdapter() {
    companion object {
        @Volatile
        var METRIC_PROCESSED = false // prevent duplicate reports being sent from the same build run
    }

    override fun afterExecute(task: Task, state: TaskState) {
        if (!state.skipped && task.name.startsWith("compile")) {
            sendMetricIfNeeded(task.project)
        }
    }

    private fun sendMetricIfNeeded(project: Project) {
        if (!METRIC_PROCESSED) {
            val disableAnalytics: Boolean = project.gradle.startParameter.isOffline || "true".equals(System.getenv()["REALM_DISABLE_ANALYTICS"], ignoreCase = true)
            if (!disableAnalytics) {
                val json = jsonPayload(project)
                sendAnalytics(json, project.logger)
            }
            METRIC_PROCESSED = true
        }
    }

    private fun jsonPayload(project: Project): String {
        val userId = ComputerIdentifierGenerator.get()
        val appId = anonymousAppId(project)
        val osType = System.getProperty("os.name")
        val osVersion = System.getProperty("os.version")

        val projectAndroidExtension: BaseExtension? = project.extensions.findByName("android") as BaseExtension?
        val minSDK = projectAndroidExtension?.defaultConfig?.minSdkVersion?.apiString
        val targetSDK = projectAndroidExtension?.defaultConfig?.targetSdkVersion?.apiString

        // Should be safe to iterate the configurations as we have left the configuration
        // phase when this is called.
        var usesSync = false
        outer@
        for (conf in project.configurations) {
            try {
                for (artifact: ResolvedArtifact in conf.resolvedConfiguration.resolvedArtifacts) {
                    // In Java we can detect Sync because we enable it through a Gradle closure.
                    // In Kotlin, this choice is currently determined by which dependency
                    // people include
                    if (artifact.name.startsWith("library-sync")) {
                        usesSync = true
                        break@outer
                    }
                }
            } catch (ignore: Exception) {
                // Some artifacts might not be able to resolve, in this case, just ignore them.
            }
        }

        // FIXME Improve metrics with details about targets, etc.
        //  https://github.com/realm/realm-kotlin/issues/127
        return """{
                   "event": "$EVENT_NAME",
                   "properties": {
                      "token": "$TOKEN",
                      "distinct_id": "$userId",
                      "Anonymized MAC Address": "$userId",
                      "Anonymized Bundle ID": "$appId",
                      "Binding": "kotlin",
                      "Language": "kotlin",
                      "Realm Version": "${RealmCompilerSubplugin.version}",
                      "Sync Enabled": ${if (usesSync) "true" else "false"},
                      "Host OS Type": "$osType",
                      "Host OS Version": "$osVersion",
                      "Target OS Minimum Version": "$minSDK",
                      "Target OS Version": "$targetSDK"
                   }
                }"""
    }

    private fun anonymousAppId(project: Project): String {
        var projectName = project.rootProject.name
        if (projectName.isEmpty()) {
            projectName = project.name
        }

        var packageName = project.group.toString()
        if (packageName.isEmpty()) {
            packageName = project.rootProject.group.toString()
        }

        return hexStringify(sha256Hash("$packageName.$projectName".toByteArray()))
    }

    @Suppress("TooGenericExceptionCaught")
    private fun sendAnalytics(json: String, logger: Logger) {
        try {
            logger.log(LogLevel.DEBUG, "REALM ANALYTICS: sending payload\n$json")
            val pool = Executors.newSingleThreadExecutor()
            try {
                pool.execute { networkQuery(json) }
                pool.awaitTermination(CONNECT_TIMEOUT + READ_TIMEOUT, TimeUnit.MILLISECONDS)
                logger.log(LogLevel.DEBUG, "REALM ANALYTICS: transfer completed")
            } catch (e: InterruptedException) {
                logger.log(LogLevel.DEBUG, "REALM ANALYTICS: transfer interrupted")
                pool.shutdownNow()
            }
        } catch (e: Exception) {
            // Analytics failing for any reason should not crash the build
            logger.log(LogLevel.DEBUG, "REALM ANALYTICS: transfer failed")
            System.err.println("Could not send analytics: $e")
        }
    }

    private fun networkQuery(jsonPayload: String) {
        try {
            val url = URL(URL_PREFIX + base64Encode(jsonPayload))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connect()
            connection.responseCode
        } catch (ignored: java.lang.Exception) {
        }
    }
}

/**
 * Generate a unique identifier for a computer. The method being used depends on the platform:
 *  - OS X:  Mac address of en0
 *  - Windows:  BIOS identifier
 *  - Linux: Machine ID provided by the OS
 */
internal object ComputerIdentifierGenerator {
    private const val UNKNOWN = "unknown"
    private val OS = System.getProperty("os.name").toLowerCase()
    @Suppress("TooGenericExceptionCaught")
    fun get(): String {
        return try {
            when {
                isWindows -> {
                    windowsIdentifier
                }
                isMac -> {
                    macOsIdentifier
                }
                isLinux -> {
                    linuxMacAddress
                }
                else -> {
                    UNKNOWN
                }
            }
        } catch (e: Exception) {
            UNKNOWN
        }
    }

    private val isWindows: Boolean
        get() = OS.contains("win")
    private val isMac: Boolean
        get() = OS.contains("mac")
    private val isLinux: Boolean
        get() = OS.contains("inux")

    @get:Throws(FileNotFoundException::class, NoSuchAlgorithmException::class)
    private val linuxMacAddress: String
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
                hexStringify(sha256Hash(id.toByteArray()))
            } finally {
                scanner?.close()
            }
        }

    @get:Throws(SocketException::class, NoSuchAlgorithmException::class)
    private val macOsIdentifier: String
        get() {
            val networkInterface = NetworkInterface.getByName("en0")
            val hardwareAddress = networkInterface.hardwareAddress
            return hexStringify(sha256Hash(hardwareAddress))
        }

    @get:Throws(IOException::class, NoSuchAlgorithmException::class)
    private val windowsIdentifier: String
        get() {
            val runtime = Runtime.getRuntime()
            val process = runtime.exec(arrayOf("wmic", "csproduct", "get", "UUID"))
            var result: String? = null
            val `is` = process.inputStream
            val sc = Scanner(process.inputStream)
            `is`.use {
                while (sc.hasNext()) {
                    val next = sc.next()
                    if (next.contains("UUID")) {
                        result = sc.next().trim { it <= ' ' }
                        break
                    }
                }
            }
            return if (result == null) UNKNOWN else hexStringify(sha256Hash(result!!.toByteArray()))
        }
}

/**
 * Encode the given string with Base64
 * @param data the string to encode
 * @return the encoded string
 * @throws UnsupportedEncodingException
 */
@Throws(UnsupportedEncodingException::class)
private fun base64Encode(data: String): String? {
    return DatatypeConverter.printBase64Binary(data.toByteArray(charset("UTF-8")))
}

/**
 * Compute the SHA-256 hash of the given byte array
 * @param data the byte array to hash
 * @return the hashed byte array
 * @throws NoSuchAlgorithmException
 */
@Throws(NoSuchAlgorithmException::class)
private fun sha256Hash(data: ByteArray?): ByteArray {
    val messageDigest = MessageDigest.getInstance("SHA-256")
    return messageDigest.digest(data)
}

/**
 * Convert a byte array to its hex-string
 * @param data the byte array to convert
 * @return the hex-string of the byte array
 */
@Suppress("MagicNumber")
private fun hexStringify(data: ByteArray): String {
    val stringBuilder = java.lang.StringBuilder()
    for (singleByte: Byte in data) {
        stringBuilder.append(((singleByte and 0xff.toByte()) + 0x100).toString(16).substring(1))
    }
    return stringBuilder.toString()
}
