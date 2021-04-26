package io.realm.internal

import io.realm.log.RealmLogger

actual object PlatformHelper {
    actual fun appFilesDirectory(): String {
        return (
            NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as NSURL
            )?.path ?: error("Could not identify default document directory")
    }

    actual fun createDefaultSystemLogger(tag: String): RealmLogger = NSLogLogger(tag)
}