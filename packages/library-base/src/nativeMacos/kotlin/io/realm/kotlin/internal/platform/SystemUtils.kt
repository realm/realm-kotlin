package io.realm.kotlin.internal.platform

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.posix.uname
import platform.posix.utsname

public actual val OS_NAME: String = "MacOS"
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
public actual val DEVICE_MANUFACTURER: String = ""
public actual val DEVICE_MODEL: String = ""
public actual val CPU_ARCH: String by lazy {
    try {
        memScoped {
            val systemInfo = alloc<utsname>()
            uname(systemInfo.ptr)
            systemInfo.machine.toKString()
        }
    } catch (e: Exception) {
        "Unknown"
    }
}

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
