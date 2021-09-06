package io.realm.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Returns a default Realm write dispatcher for a Realm opened on the calling thread.
 */
expect fun singleThreadDispatcher(id: String): CoroutineDispatcher

/**
 * Runs a new coroutine and **blocks** the current thread _interruptibly_ until its completion.
 *
 * This just exposes a common runBlocking for our supported platforms, as this is not available in
 * Kotlin's common packages due to lack of JS implementation.
 *
 * See documentation in one of the specific Kotlin implementations for further details.
 */
expect fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T
