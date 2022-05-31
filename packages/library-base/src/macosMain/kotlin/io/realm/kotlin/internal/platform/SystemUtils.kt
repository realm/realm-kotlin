package io.realm.kotlin.internal.platform

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String {
    return platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
}
