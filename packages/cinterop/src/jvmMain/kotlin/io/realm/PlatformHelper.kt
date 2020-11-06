package io.realm

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    @Suppress("FunctionOnlyReturningConstant")
    actual fun directory(): String {
        return ""
    }
}
