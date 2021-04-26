package io.realm.util

import java.io.File
import java.nio.file.Files
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString

actual object PlatformUtils {
    @ExperimentalPathApi
    actual fun createTempDir(): String {
        return Files.createTempDirectory("android_tests").absolutePathString()
    }

    actual fun deleteTempDir(path: String) {
        File(path).deleteRecursively()
    }
}