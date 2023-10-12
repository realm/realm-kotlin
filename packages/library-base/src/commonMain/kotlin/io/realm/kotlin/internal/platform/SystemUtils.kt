@file:JvmName("SystemUtilsJvm")
package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
import kotlin.jvm.JvmName
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

// TODO All methods and properties in this file are public as they are used by both `library-sync`
//  and tests.

/**
 * Runtime identifier. Either 'JVM', 'Android' or 'Native'
 */
public expect val RUNTIME: SyncConnectionParams.Runtime

/**
 * Version of the runtime. On Android this this the Android version, e.g. `33`.
 * On JVM this is the JVM version, e.g. `11.0.15`
 * On Native, the empty string is returned.
 */
public expect val RUNTIME_VERSION: String

/**
 * Which CPU architecture is the code running on.
 */
public expect val CPU_ARCH: String

/**
 * Operating system name.
 */
public expect val OS_NAME: String

/**
 * Operating system version.
 */
public expect val OS_VERSION: String

/**
 * For mobile devices, this will be the manufacturer of the phone.
 *
 * On Desktop, this returns the empty string.
 */
public expect val DEVICE_MANUFACTURER: String

/**
 * For mobile devices, this will return the unique model number of
 * the phone, e.g `GT-I9100`
 *
 * On Desktop, this returns the empty string..
 */
public expect val DEVICE_MODEL: String

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
public expect fun createDefaultSystemLogger(tag: String, logLevel: LogLevel = LogLevel.NONE): RealmLogger

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
