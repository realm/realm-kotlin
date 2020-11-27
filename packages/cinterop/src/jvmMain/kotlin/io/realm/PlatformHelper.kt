package io.realm

actual object PlatformHelper {
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        return "."
    }
}
