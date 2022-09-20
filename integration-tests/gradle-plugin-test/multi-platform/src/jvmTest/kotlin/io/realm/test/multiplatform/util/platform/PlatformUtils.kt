package io.realm.test.multiplatform.util.platform

import java.io.File
import java.nio.file.Files
import kotlin.io.path.absolutePathString

actual object PlatformUtils {
    actual fun createTempDir(prefix: String): String {
        return Files.createTempDirectory("$prefix-jvm_tests").absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        File(path).deleteRecursively()
    }
}