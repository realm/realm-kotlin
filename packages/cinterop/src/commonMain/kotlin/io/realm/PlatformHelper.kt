package io.realm

expect object PlatformHelper {

    // Returns the root directory of the platform's App data
    fun directory(): String
}
