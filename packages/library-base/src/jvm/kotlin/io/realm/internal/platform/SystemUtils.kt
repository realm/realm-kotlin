package io.realm.internal.platform

import java.io.File

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val RUNTIME: String = "JVM"

public actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

public actual fun <T> T.freeze(): T = this

public actual val <T> T.isFrozen: Boolean
    get() = false

public actual fun Any.ensureNeverFrozen() {}

// Depend on filesystem API's to handle edge cases around creating paths.
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