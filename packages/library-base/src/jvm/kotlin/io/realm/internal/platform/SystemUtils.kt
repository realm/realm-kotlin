package io.realm.internal.platform

import android.os.Build
import io.realm.RealmInstant
import java.io.File
import java.time.Clock
import java.util.Date

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val RUNTIME: String = "JVM"

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val PATH_SEPARATOR: String = File.separator

public actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

public actual fun <T> T.freeze(): T = this

public actual val <T> T.isFrozen: Boolean
    get() = false

public actual fun Any.ensureNeverFrozen() {}

public actual fun fileExists(path: String): Boolean =
    File(path).let { it.exists() && it.isFile }

public actual fun directoryExists(path: String): Boolean =
    File(path).let { it.exists() && it.isDirectory }

public actual fun canWrite(path: String): Boolean = File(path).canWrite()

public actual fun prepareRealmDirectoryPath(directoryPath: String): String {
    preparePath(directoryPath)
    return File(directoryPath).absolutePath
}

// Depend on filesystem API's to handle edge cases around creating paths.
public actual fun prepareRealmFilePath(directoryPath: String, filename: String): String {
    preparePath(directoryPath)
    return File(directoryPath, filename).absolutePath
}

private fun preparePath(directoryPath: String) {
    val dir = File(directoryPath).absoluteFile
    if (!dir.exists()) {
        if (!dir.mkdirs()) {
            throw IllegalStateException("Directories for Realm file could not be created: $directoryPath")
        }
    }
    if (dir.isFile) {
        throw IllegalArgumentException("Provided directory is a file: $directoryPath")
    }
}
