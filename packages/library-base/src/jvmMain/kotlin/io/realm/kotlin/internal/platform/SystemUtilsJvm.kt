package io.realm.kotlin.internal.platform

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

public actual val OS_NAME: String = System.getProperty("os.name")
public actual val OS_VERSION: String = System.getProperty("os.version")

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String = System.getProperty("user.dir") ?: "."

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
