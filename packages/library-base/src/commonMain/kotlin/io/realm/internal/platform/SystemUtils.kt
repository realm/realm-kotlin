package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

/**
 * Runtime identifier. Either 'JVM' or 'Native'
 */
expect val RUNTIME: String

/**
 * Operation system name.
 */
expect val OS_NAME: String

/**
 * Operation system version.
 */
expect val OS_VERSION: String

/**
 * Returns the root directory of the platform's App data.
 */
expect fun appFilesDirectory(): String

/**
 * Returns the default logger for the platform.
 */
expect fun createDefaultSystemLogger(tag: String, logLevel: LogLevel = LogLevel.NONE): RealmLogger

/**
 * Method to freeze state.
 * Calls the platform implementation of 'freeze' on native, and is a noop on other platforms.
 *
 * Note, this method refers to Kotlin Natives notion of frozen objects, and not Realms variant
 * of frozen objects.
 */
expect fun <T> T.freeze(): T

/**
 * Determine if object is frozen.
 * Will return false on non-native platforms.
 */
expect val <T> T.isFrozen: Boolean

/**
 * Call on an object which should never be frozen.
 * Will help debug when something inadvertently is.
 * This is a noop on non-native platforms.
 */
expect fun Any.ensureNeverFrozen()

/**
 * Return the current thread id.
 */
expect fun threadId(): ULong
