package io.realm

import io.realm.internal.RealmInitializer

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    actual fun appFilesDirectory(): String {
        return RealmInitializer.filesDir.absolutePath
    }
}
