package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

@Suppress("MayBeConst") // Cannot make expect/actual const
actual val OS_NAME: String = "Android"
actual val OS_VERSION: String = android.os.Build.VERSION.RELEASE

// Returns the root directory of the platform's App data
actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    LogCatLogger(tag, logLevel)
