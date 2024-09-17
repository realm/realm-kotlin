@file:JvmName("SystemUtilsJvm")
package io.realm.kotlin.internal.platform

import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
import kotlin.jvm.JvmName
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

/**
 * Operating system name.
 */
public expect val OS_NAME: String

/**
 * Path separator.
 */
public expect val PATH_SEPARATOR: String

/**
 * Construct a path from individual components
 */
public fun pathOf(vararg pathParts: String): String {
    return pathParts.joinToString(PATH_SEPARATOR)
}

/**
 * Returns the root directory of the platform's App data.
 */
public expect fun appFilesDirectory(): String

/**
 * Copies an asset file into location if the realm files does not exist.
 *
 * The asset file is located according to the platform conventions:
 * - Android: Through android.content.res.AssetManager.open(assetFilename)
 * - JVM: Class<T>.javaClass.classLoader.getResource(assetFilename)
 * - Darwin: NSBundle.mainBundle.pathForResource(assetFilenameBase, assetFilenameExtension)
 */
public expect fun copyAssetFile(realmFilePath: String, assetFilename: String, sha256Checksum: String?)

/**
 * Checks whether a file in the specified path exists.
 */
public expect fun fileExists(path: String): Boolean

/**
 * Checks whether a directory in the specified path exists.
 */
public expect fun directoryExists(path: String): Boolean

/**
 * Checks whether the application can write data in the specified path.
 */
public expect fun canWrite(path: String): Boolean

/**
 * Normalize and prepare a platform dependant path to a directory.
 *
 * This method will normalize the path to a standard format on the platform, validate the directory
 * name and create any intermediate directories required to do so.
 *
 * @throws IllegalArgumentException if the directory path is somehow not valid or the required
 * directories cannot be created.
 * See https://github.com/realm/realm-kotlin/issues/699
 */
public expect fun prepareRealmDirectoryPath(directoryPath: String): String

/**
 * Normalize and prepare a platform dependant path to a realm file.
 *
 * This method will normalize the path to a standard format on the platform, validate the filename
 * and create any intermediate directories required to store the file.
 *
 * @throws IllegalArgumentException if the directory path is somehow not valid or the required
 * directories cannot be created.
 * See https://github.com/realm/realm-kotlin/issues/699
 */
public expect fun prepareRealmFilePath(directoryPath: String, filename: String): String

/**
 * Returns the default logger for the platform.
 */
public expect fun createDefaultSystemLogger(tag: String): RealmLogger

/**
 * Return the current thread id.
 */
public expect fun threadId(): ULong

/**
 * Returns UNIX epoch time in seconds.
 */
public expect fun epochInSeconds(): Long

/**
 * Returns a RealmInstant representing the time that has passed since the Unix epoch.
 * For Android targets < API 26 there's no nano seconds precision.
 */
public expect fun currentTime(): RealmInstant

/**
 * Returns the type of a mutable property.
 *
 * This method is exposed because `returnType` isn't available in Common, but is available on
 * JVM and macOS: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-callable/
 */
public expect fun <K : Any?, V : Any?> returnType(field: KMutableProperty1<K, V>): KType

/**
 * Returns whether or not we are running on Windows
 */
public expect fun isWindows(): Boolean

/**
 * Returns the identity hashcode for a given object.
 */
internal expect fun identityHashCode(obj: Any?): Int
