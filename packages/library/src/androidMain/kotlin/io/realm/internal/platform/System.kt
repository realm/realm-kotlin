package io.realm.internal.platform

import io.realm.log.RealmLogger

// Returns the root directory of the platform's App data
actual fun appFilesDirectory(): String = RealmInitializer.filesDir.absolutePath

// Returns the default logger for the platform
actual fun createDefaultSystemLogger(tag: String): RealmLogger = LogCatLogger(tag)


actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

actual fun <T> T.freeze(): T = this

actual val <T> T.isFrozen: Boolean
    get() = false

actual fun Any.ensureNeverFrozen() {}

