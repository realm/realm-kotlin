package io.realm

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

actual object PlatformHelper {
    actual fun appFilesDirectory(): String {
        return (
            // TODO POSTPONED Consider differentiating as ~/Documents might not be the most
            //  intuitive location for macos
            NSFileManager.defaultManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as NSURL
            )?.path ?: error("Could not identify default document directory")
    }
}
