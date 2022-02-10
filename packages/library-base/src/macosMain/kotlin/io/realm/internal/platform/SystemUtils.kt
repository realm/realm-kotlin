package io.realm.internal.platform

@Suppress("FunctionOnlyReturningConstant")
actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
