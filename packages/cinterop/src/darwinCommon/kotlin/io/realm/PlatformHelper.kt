package io.realm

import platform.Foundation.NSFileManager
import platform.Foundation.temporaryDirectory

actual object PlatformHelper {

    actual fun appFilesDirectory(): String {
        // FIXME Do not know the convention for where to put data
        val currentDirectoryPath: String? = NSFileManager.defaultManager.temporaryDirectory().path
        return currentDirectoryPath!!
    }
}
