package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

public actual val OS_NAME: String = System.getProperty("os.name")
public actual val OS_VERSION: String = System.getProperty("os.version")

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String = "."

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
