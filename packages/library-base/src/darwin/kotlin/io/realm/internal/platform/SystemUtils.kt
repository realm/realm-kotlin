package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSince1970
import platform.posix.pthread_threadid_np
import kotlin.native.concurrent.ensureNeverFrozen
import kotlin.native.concurrent.freeze
import kotlin.native.concurrent.isFrozen

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val RUNTIME: String = "Native"
// These causes memory mapping rendering MemoryTests to fail, so only initialize them if actually needed
public actual val OS_NAME: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemName() }
public actual val OS_VERSION: String by lazy { NSProcessInfo.Companion.processInfo().operatingSystemVersionString }
@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val PATH_SEPARATOR: String = "/"

public actual fun createDefaultSystemLogger(tag: String, logLevel: LogLevel): RealmLogger =
    NSLogLogger(tag, logLevel)

public actual fun threadId(): ULong {
    memScoped {
        val tidVar = alloc<ULongVar>()
        pthread_threadid_np(null, tidVar.ptr)
        return tidVar.value
    }
}

public actual fun epochInSeconds(): Long =
    NSDate().timeIntervalSince1970().toLong()

public actual fun <T> T.freeze(): T = this.freeze()

public actual val <T> T.isFrozen: Boolean
    get() = this.isFrozen

public actual fun Any.ensureNeverFrozen(): Unit = this.ensureNeverFrozen()

public actual fun fileExists(path: String): Boolean {
    val fm = platform.Foundation.NSFileManager.defaultManager
    memScoped {
        val isDir = alloc<BooleanVar>()
        val exists = fm.fileExistsAtPath(path, isDir.ptr)
        return !isDir.value && exists
    }
}

public actual fun directoryExists(path: String): Boolean {
    val fm = platform.Foundation.NSFileManager.defaultManager
    memScoped {
        val isDir = alloc<BooleanVar>()
        val exists = fm.fileExistsAtPath(path, isDir.ptr)
        return isDir.value && exists
    }
}

public actual fun canWrite(path: String): Boolean {
    val fm = platform.Foundation.NSFileManager.defaultManager
    return fm.isWritableFileAtPath(path)
}

public actual fun prepareRealmDirectoryPath(directoryPath: String): String {
    val dir = NSURL.fileURLWithPath(directoryPath, isDirectory = true)
    preparePath(directoryPath, dir)
    return NSURL.fileURLWithPath(directoryPath).absoluteString?.removePrefix("file://")
        ?: throw IllegalArgumentException("Could not resolve path components: '$directoryPath'.")
}

// Depend on filesystem API's to handle edge cases around creating paths.
public actual fun prepareRealmFilePath(directoryPath: String, filename: String): String {
    val dir = NSURL.fileURLWithPath(directoryPath, isDirectory = true)
    preparePath(directoryPath, dir)
    return NSURL.fileURLWithPath(filename, dir).absoluteString?.removePrefix("file://")
        ?: throw IllegalArgumentException("Could not resolve path components: '$directoryPath' and '$filename'.")
}

private fun preparePath(directoryPath: String, dir: NSURL) {
    val fm = platform.Foundation.NSFileManager.defaultManager
    memScoped {
        val isDir = alloc<BooleanVar>()
        val exists = fm.fileExistsAtPath(directoryPath, isDir.ptr)
        if (!exists) {
            val errorPtr = alloc<ObjCObjectVar<NSError?>>()
            fm.createDirectoryAtURL(
                url = dir,
                withIntermediateDirectories = true,
                attributes = mapOf<Any?, Any>(),
                errorPtr.ptr
            )
            errorPtr.ptr.pointed.value?.let {
                throw IllegalArgumentException("Directories for Realm file could not be created: $directoryPath. Underlying cause: $it")
            }
        }
        if (exists && !isDir.value) {
            throw IllegalArgumentException("Provided directory is a file: $directoryPath")
        }
    }
}
