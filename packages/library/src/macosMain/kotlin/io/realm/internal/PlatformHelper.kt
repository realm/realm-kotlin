package io.realm.internal

import io.realm.log.RealmLogger

actual object PlatformHelper {
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        return "."
    }

    actual fun createDefaultSystemLogger(tag: String): RealmLogger = NSLogLogger(tag)
}