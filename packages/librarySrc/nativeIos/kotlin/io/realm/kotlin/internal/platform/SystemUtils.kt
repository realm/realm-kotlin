package io.realm.kotlin.internal.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

internal actual val OS_NAME: String = "iOS"
internal actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
internal actual val DEVICE_MANUFACTURER: String by lazy { UIDevice.currentDevice.model }
internal actual val DEVICE_MODEL: String by lazy {
    try {
        // On iOS devices this will report the underlying phone model:
        // https://stackoverflow.com/a/11197770/1389357
        // On simulators this will report the underlying architecture like x86_64 or arm64.
        memScoped {
            val systemInfo = alloc<utsname>()
            uname(systemInfo.ptr)
            systemInfo.machine.toKString()
        }
    } catch (e: Exception) {
        "Unknown"
    }
}
// CPU architecture can by looking at DEVICE_MODEL, but encoding the mapping in code will be
// difficult to maintain, so is left out.
internal actual val CPU_ARCH: String = ""

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
