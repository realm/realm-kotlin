package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

actual val OS_NAME: String = System.getProperty("os.name")
actual val OS_VERSION: String = System.getProperty("os.version")

@Suppress("FunctionOnlyReturningConstant")
actual fun appFilesDirectory(): String = "."

actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
