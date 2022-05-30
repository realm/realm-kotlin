package io.realm.kotlin.internal.platform

import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val OS_NAME: String = "Android"
public actual val OS_VERSION: String = android.os.Build.VERSION.RELEASE

// Returns the root directory of the platform's App data
public actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    LogCatLogger(tag, logLevel)
