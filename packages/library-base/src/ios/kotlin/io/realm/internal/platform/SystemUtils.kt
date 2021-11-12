package io.realm.internal.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual fun appFilesDirectory(): String {
    return (
            NSFileManager.defaultManager.URLForDirectory(
                NSDocumentDirectory,
                NSUserDomainMask,
                null,
                true,
                null
            ) as NSURL
            ).path ?: error("Could not identify default document directory")
}
