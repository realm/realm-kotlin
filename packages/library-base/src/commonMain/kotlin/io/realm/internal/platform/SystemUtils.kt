package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

/**
 * Runtime identifier. Either 'JVM' or 'Native'
 */
internal expect val RUNTIME: String

/**
 * Operation system name.
 */
internal expect val OS_NAME: String

/**
 * Operation system version.
 */
internal expect val OS_VERSION: String

/**
 * Returns the root directory of the platform's App data.
 */
internal expect fun appFilesDirectory(): String

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
