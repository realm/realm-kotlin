package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import java.io.File

public actual val OS_NAME: String = System.getProperty("os.name")
public actual val OS_VERSION: String = System.getProperty("os.version")

@Suppress("FunctionOnlyReturningConstant")
public actual fun appFilesDirectory(): String = System.getProperty("user.dir") ?: "."

// Depend on JVM filesystem API's to handle edge cases around creating paths.
public actual fun prepareRealmFilePath(directoryPath: String, filename: String): String {
    val dir = File(directoryPath).absoluteFile
    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            throw IllegalStateException("Directories for Realm file could not be created: $directoryPath")
        }
    }
    if (dir.isFile) {
        throw IllegalArgumentException("Provided directory is a file: $directoryPath")
    }
    return File(directoryPath, filename).absolutePath
}

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    StdOutLogger(tag, logLevel)
