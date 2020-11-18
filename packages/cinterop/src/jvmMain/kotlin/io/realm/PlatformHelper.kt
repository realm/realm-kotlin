package io.realm

actual object PlatformHelper {

    // Returns the root directory of the platform's App data
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        // FIXME What is the standard default location for non-Android JVM builds.
        //  https://github.com/realm/realm-kotlin/issues/75
        return "."
    }
}
