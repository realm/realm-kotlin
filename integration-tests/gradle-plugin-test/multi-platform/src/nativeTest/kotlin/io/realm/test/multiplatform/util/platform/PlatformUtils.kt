package io.realm.test.multiplatform.util.platform

import kotlinx.cinterop.cstr

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        // X is a special char which will be replace by mkdtemp template
        val mask = prefix.replace('X', 'Z', ignoreCase = true)
        val path = "${platform.Foundation.NSTemporaryDirectory()}$mask-native_tests"
        platform.posix.mkdtemp(path.cstr)
        return path
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(
            platform.Foundation.NSURL(fileURLWithPath = path),
            null
        )
    }
}