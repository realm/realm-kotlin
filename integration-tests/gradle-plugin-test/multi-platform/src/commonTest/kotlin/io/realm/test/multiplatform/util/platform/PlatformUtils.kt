package io.realm.test.multiplatform.util.platform

import io.realm.test.multiplatform.util.Utils

expect object PlatformUtils {
    fun createTempDir(prefix: String = Utils.createRandomString(16)): String
    fun deleteTempDir(path: String)
}