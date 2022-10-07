package io.realm.kotlin.internal.platform

/**
 * Method to freeze state.
 * Calls the platform implementation of 'freeze' on native, and is a noop on other platforms.
 *
 * Note, this method refers to Kotlin Natives notion of frozen objects, and not Realms variant
 * of frozen objects.
 *
 * From Kotlin 1.7.20 freeze is deprecated, so this is a no-op on all platforms.
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
