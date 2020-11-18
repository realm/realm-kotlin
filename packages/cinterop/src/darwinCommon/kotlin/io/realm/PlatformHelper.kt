package io.realm

import platform.Foundation.NSFileManager
import platform.Foundation.temporaryDirectory

actual object PlatformHelper {

    actual fun appFilesDirectory(): String {
        // FIXME What is the standard default location for realms on Darwin...and does this have to
        //  be differentiated for macos vs. iOS
        //  https://github.com/realm/realm-kotlin/issues/75
        val currentDirectoryPath: String? = NSFileManager.defaultManager.temporaryDirectory().path
        return currentDirectoryPath!!
    }
}
