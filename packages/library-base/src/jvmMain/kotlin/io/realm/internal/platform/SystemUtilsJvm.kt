package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

internal actual val OS_NAME: String = System.getProperty("os.name")
internal actual val OS_VERSION: String = System.getProperty("os.version")

@Suppress("FunctionOnlyReturningConstant")
internal actual fun appFilesDirectory(): String = "."

internal actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
