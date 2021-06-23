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
 * Platform agnostic _WeakReference_ wrapper.
 *
 * Basically just wrapping underlying platform implementations.
 */
expect class WeakReference<T : Any>(referred: T) {
    fun clear()
    fun get(): T?
}
