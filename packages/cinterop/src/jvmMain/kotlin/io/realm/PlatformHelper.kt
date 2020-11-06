package io.realm

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    actual fun directory(): String {
        return ""
    }

}
