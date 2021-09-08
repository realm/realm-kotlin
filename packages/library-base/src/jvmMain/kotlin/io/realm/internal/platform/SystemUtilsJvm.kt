package io.realm.internal.platform

import io.realm.log.RealmLogger

@Suppress("FunctionOnlyReturningConstant")
actual fun appFilesDirectory(): String {
    return "."
}

actual fun createDefaultSystemLogger(tag: String): RealmLogger = StdOutLogger(tag)
