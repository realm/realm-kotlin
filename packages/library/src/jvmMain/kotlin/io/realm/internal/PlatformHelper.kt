package io.realm.internal

import io.realm.log.RealmLogger

actual object PlatformHelper {
    @Suppress("FunctionOnlyReturningConstant")
    actual fun appFilesDirectory(): String {
        // FIXME What is the standard default location for non-Android JVM builds.
        //  https://github.com/realm/realm-kotlin/issues/75
        return "."
    }

    actual fun createDefaultSystemLogger(tag: String): RealmLogger = StdOutLogger(tag)

}