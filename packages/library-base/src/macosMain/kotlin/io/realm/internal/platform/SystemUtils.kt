package io.realm.internal.platform

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
