package io.realm.internal

import io.realm.log.RealmLogger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

expect object PlatformHelper {

    // Returns the root directory of the platform's App data
    fun appFilesDirectory(): String

    // Returns the default logger for the platform
    actual fun createDefaultSystemLogger(tag: String): RealmLogger
}

/**
 * Runs a new coroutine and **blocks** the current thread _interruptibly_ until its completion.
 *
 * This just exposes a common runBlocking for our supported platforms, as this is not available in
 * Kotlin's common packages due to lack of JS implementation.
 *
 * See documentation in one of the specific Kotlin implementations for further details.
 */
expect fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T

/**
 * Returns a default Realm write dispatcher for a Realm opened on the calling thread.
 */
expect fun singleThreadDispatcher(id: String): CoroutineDispatcher

/**
 * Return the current thread id.
 */
expect fun threadId(): ULong

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
 * Platform agnostic _WeakReference_ wrapper.
 *
 * Basically just wrapping underlying platform implementations.
 */
expect class WeakReference<T : Any>(referred: T) {
    fun clear()
    fun get(): T?
}
