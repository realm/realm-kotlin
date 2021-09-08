package io.realm.internal.platform

import io.realm.log.RealmLogger

// Returns the root directory of the platform's App data
actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
actual fun createDefaultSystemLogger(tag: String): RealmLogger = LogCatLogger(tag)
