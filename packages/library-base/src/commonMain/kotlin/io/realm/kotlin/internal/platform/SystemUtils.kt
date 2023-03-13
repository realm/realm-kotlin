package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.interop.SyncConnectionParams
import io.realm.kotlin.log.LogLevel
import io.realm.kotlin.log.RealmLogger
import io.realm.kotlin.types.RealmInstant
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

// TODO All methods and properties in this file are internal as they are used by both `library-sync`
//  and tests.

/**
 * Runtime identifier. Either 'JVM', 'Android' or 'Native'
 */
internal expect val RUNTIME: SyncConnectionParams.Runtime

/**
 * Version of the runtime. On Android this this the Android version, e.g. `33`.
 * On JVM this is the JVM version, e.g. `11.0.15`
 * On Native, the empty string is returned.
 */
internal expect val RUNTIME_VERSION: String

/**
 * Which CPU architecture is the code running on.
 */
internal expect val CPU_ARCH: String

/**
 * Operating system name.
 */
internal expect val OS_NAME: String

/**
 * Operating system version.
 */
internal expect val OS_VERSION: String

/**
 * For mobile devices, this will be the manufacturer of the phone.
 *
 * On Desktop, this returns the empty string.
 */
internal expect val DEVICE_MANUFACTURER: String

/**
 * For mobile devices, this will return the unique model number of
 * the phone, e.g `GT-I9100`
 *
 * On Desktop, this returns the empty string..
 */
internal expect val DEVICE_MODEL: String

/**
 * Path separator.
 */
internal expect val PATH_SEPARATOR: String

/**
 * Returns the root directory of the platform's App data.
 */
internal expect fun appFilesDirectory(): String

/**
 * Checks whether a file in the specified path exists.
 */
internal expect fun fileExists(path: String): Boolean

/**
 * Checks whether a directory in the specified path exists.
 */
internal expect fun directoryExists(path: String): Boolean

/**
 * Checks whether the application can write data in the specified path.
 */
internal expect fun canWrite(path: String): Boolean

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
internal expect fun prepareRealmDirectoryPath(directoryPath: String): String

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
internal expect fun prepareRealmFilePath(directoryPath: String, filename: String): String

/**
 * Returns the default logger for the platform.
 */
internal expect fun createDefaultSystemLogger(tag: String, logLevel: LogLevel = LogLevel.NONE): RealmLogger

/**
 * Method to freeze state.
 * Calls the platform implementation of 'freeze' on native, and is a noop on other platforms.
 *
 * Note, this method refers to Kotlin Natives notion of frozen objects, and not Realms variant
 * of frozen objects.
 *
 * From Kotlin 1.7.20 freeze is deprecated, so this is a no-op on all platforms.
 */
internal expect fun <T> T.freeze(): T

/**
 * Determine if object is frozen.
 * Will return false on non-native platforms.
 */
internal expect val <T> T.isFrozen: Boolean

/**
 * Call on an object which should never be frozen.
 * Will help debug when something inadvertently is.
 * This is a noop on non-native platforms.
 */
internal expect fun Any.ensureNeverFrozen()

/**
 * Return the current thread id.
 */
internal expect fun threadId(): ULong

/**
 * Returns UNIX epoch time in seconds.
 */
internal expect fun epochInSeconds(): Long

/**
 * Returns a RealmInstant representing the time that has passed since the Unix epoch.
 */
internal expect fun currentTime(): RealmInstant

/**
 * Returns the type of a mutable property.
 *
 * This method is exposed because `returnType` isn't available in Common, but is available on
 * JVM and macOS: https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-callable/
 */
internal expect fun <K : Any?, V : Any?> returnType(field: KMutableProperty1<K, V>): KType
