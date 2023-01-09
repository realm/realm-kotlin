package io.realm.kotlin.internal.platform

import platform.Foundation.NSProcessInfo

// See // See https://stackoverflow.com/questions/8058151/how-does-system-profiler-retrieve-the-full-mac-hardware-identifier
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
public actual val OS_NAME: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemName() }
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
public actual val DEVICE_MANUFACTURER: String by lazy { "" }
public actual val DEVICE_MODEL: String by lazy { "" }

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
