package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
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
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

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

/**
 * Inspired by: https://github.com/Kotlin/kotlinx-datetime/blob/master/core/darwin/src/Converters.kt
 * and https://github.com/Kotlin/kotlinx-datetime/blob/master/core/native/src/Instant.kt.
 *
 * Even though Darwin only uses millisecond precision, it is possible that [date] uses larger resolution, storing
 * microseconds or even nanoseconds. In this case, the sub-millisecond parts of [date] are rounded to the nearest
 * millisecond, given that they are likely to be conversion artifacts.
 *
 * Since internalNow() should only logically return a value after the Unix epoch, it is safe to create a RealmInstant
 * without considering having to pass negative nanoseconds.
 */
@Suppress("MagicNumber")
internal actual fun currentTime(): RealmInstant {
    val secs = NSDate().timeIntervalSince1970
    val millis = (secs * 1000 + if (secs > 0) 0.5 else -0.5).toLong()
    return if (millis < RealmInstant.MIN.epochSeconds * 1000) {
        RealmInstant.MIN
    } else if (millis > RealmInstant.MAX.epochSeconds * 1000) {
        RealmInstant.MAX
    } else {
        RealmInstantImpl(
            millis.floorDiv(1000.toLong()),
            (millis.mod(1000.toLong()) * 1000).toInt()
        )
    }
}

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

public actual fun <K : Any?, V : Any?> returnType(field: KMutableProperty1<K, V>): KType {
    return field.returnType
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
