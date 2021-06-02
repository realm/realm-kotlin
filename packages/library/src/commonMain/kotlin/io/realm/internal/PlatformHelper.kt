package io.realm.internal

import io.realm.log.RealmLogger

expect object PlatformHelper {

    // Returns the root directory of the platform's App data
    fun appFilesDirectory(): String

    // Returns the default logger for the platform
    actual fun createDefaultSystemLogger(tag: String): RealmLogger
}

expect val transactionMap: MutableMap<SuspendableWriter, Boolean>
