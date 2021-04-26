package io.realm.util

actual object PlatformUtils {
    actual fun createTempDir(): String {
        val mask = "${platform.Foundation.NSTemporaryDirectory()}realm_test_.XXXXXX"
        return platform.posix.mkdtemp(mask.cstr)!!.toKString()
    }

    actual fun deleteTempDir(path: String) {
        platform.Foundation.NSFileManager.defaultManager.removeItemAtURL(platform.Foundation.NSURL(fileURLWithPath = path), null)
    }
}
