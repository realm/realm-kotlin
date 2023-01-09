package io.realm.kotlin.internal.platform

import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice

// See https://stackoverflow.com/questions/8058151/how-does-system-profiler-retrieve-the-full-mac-hardware-identifier
public actual val OS_NAME: String by lazy { "iOS" }
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
public actual val DEVICE_MANUFACTURER: String by lazy { UIDevice.currentDevice.model }
public actual val DEVICE_MODEL: String by lazy { "" }

public actual fun appFilesDirectory(): String {
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
