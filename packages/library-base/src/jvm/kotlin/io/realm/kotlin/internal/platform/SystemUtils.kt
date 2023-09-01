package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.Constants.FILE_COPY_BUFFER_SIZE
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.util.Exceptions
import io.realm.kotlin.types.RealmInstant
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import java.time.Clock.systemUTC
import java.util.concurrent.TimeUnit
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

@Suppress("MayBeConst") // Cannot make expect/actual const
public actual val PATH_SEPARATOR: String = File.separator

public actual fun threadId(): ULong {
    return Thread.currentThread().id.toULong()
}

public actual fun epochInSeconds(): Long =
    TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())

/**
* Since internalNow() should only logically return a value after the Unix epoch, it is safe to create a RealmInstant
* without considering having to pass negative nanoseconds.
*/
public actual fun currentTime(): RealmInstant {
    val jtInstant = systemUTC().instant()
    return RealmInstantImpl(jtInstant.epochSecond, jtInstant.nano)
}

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

private fun copyStream(inputStream: InputStream, outputStream: OutputStream) {
    val buf = ByteArray(FILE_COPY_BUFFER_SIZE)
    var bytesRead: Int
    while ((inputStream.read(buf).also { bytesRead = it }) > -1) {
        outputStream.write(buf, 0, bytesRead)
    }
}

/**
 * Open an input stream from the asset file according to the platform conventions.
 * - Android: Through android.content.res.AssetManager.open(assetFilename)
 * - JVM: Class<T>.javaClass.classLoader.getResource(assetFilename)
 *
 * @throws Exceptions.assetFileNotFound if the file is not found.
 */
public expect fun assetFileAsStream(assetFilename: String): InputStream

@Suppress("NestedBlockDepth")
public actual fun copyAssetFile(
    realmFilePath: String,
    assetFilename: String,
    sha256Checksum: String?
) {
    assetFileAsStream(assetFilename).let { inputStream ->
        if (sha256Checksum != null) {
            DigestInputStream(inputStream, MessageDigest.getInstance("SHA-256"))
        } else {
            inputStream
        }
    }.use { assetStream ->
        val outputFile = File(realmFilePath)
        try {
            outputFile.outputStream().use { outputStream ->
                copyStream(assetStream, outputStream)
            }
            if (sha256Checksum != null && assetStream is DigestInputStream) {
                val actual = assetStream.messageDigest.digest()
                    .fold("", { str, element -> str + "%02x".format(element) })
                if (actual != sha256Checksum) {
                    throw Exceptions.assetFileChecksumMismatch(
                        assetFilename,
                        sha256Checksum,
                        actual
                    )
                }
            }
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            throw e
        }
    }
}

public actual fun <K : Any?, V : Any?> returnType(field: KMutableProperty1<K, V>): KType {
    return field.returnType
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

public actual fun isWindows(): Boolean = OS_NAME.contains("windows", ignoreCase = true)

internal actual fun identityHashCode(obj: Any?): Int = System.identityHashCode(obj)
