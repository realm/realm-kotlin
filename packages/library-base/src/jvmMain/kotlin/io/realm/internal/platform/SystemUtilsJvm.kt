package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

@Suppress("FunctionOnlyReturningConstant")
actual fun appFilesDirectory(): String = "."

actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
