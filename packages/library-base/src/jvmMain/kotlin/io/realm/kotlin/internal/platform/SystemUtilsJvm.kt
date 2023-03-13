package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

internal actual val RUNTIME: SyncConnectionParams.Runtime = SyncConnectionParams.Runtime.JVM
internal actual val RUNTIME_VERSION: String = System.getProperty("java.version")
internal actual val CPU_ARCH: String = System.getProperty("os.arch")
internal actual val OS_NAME: String = System.getProperty("os.name")
internal actual val OS_VERSION: String = System.getProperty("os.version")
internal actual val DEVICE_MANUFACTURER: String = ""
internal actual val DEVICE_MODEL: String = ""

@Suppress("FunctionOnlyReturningConstant")
internal actual fun appFilesDirectory(): String = System.getProperty("user.dir") ?: "."

internal actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
