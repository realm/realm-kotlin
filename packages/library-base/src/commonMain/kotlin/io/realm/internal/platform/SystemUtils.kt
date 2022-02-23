package io.realm.internal.platform

import io.realm.log.LogLevel
import io.realm.log.RealmLogger

// TODO All methods and properties in this file are public as they are used by both `library-sync`
//  and tests.

/**
 * Runtime identifier. Either 'JVM' or 'Native'
 */
public expect val RUNTIME: String

/**
 * Operation system name.
 */
public expect val OS_NAME: String

/**
 * Operation system version.
 */
public expect val OS_VERSION: String

/**
 * Returns the root directory of the platform's App data.
 */
public expect fun appFilesDirectory(): String

/**
 * Returns the default logger for the platform.
 */
public expect fun createDefaultSystemLogger(tag: String, logLevel: LogLevel = LogLevel.NONE): RealmLogger

/**
 * Method to freeze state.
 * Calls the platform implementation of 'freeze' on native, and is a noop on other platforms.
 *
 * Note, this method refers to Kotlin Natives notion of frozen objects, and not Realms variant
 * of frozen objects.
 */
public expect fun <T> T.freeze(): T

/**
 * Determine if object is frozen.
 * Will return false on non-native platforms.
 */
public expect val <T> T.isFrozen: Boolean

/**
 * Call on an object which should never be frozen.
 * Will help debug when something inadvertently is.
 * This is a noop on non-native platforms.
 */
public expect fun Any.ensureNeverFrozen()

/**
 * Return the current thread id.
 */
public expect fun threadId(): ULong
