package io.realm.kotlin.internal.platform

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

// TODO Methods in this file are public as they are used extensively by the `test` module. Consider
//  moving these to a `shared` module or similar that all other modules depend on. Then visibility
//  can be controlled through the normal `api/implementation` dependency mechanisms.

/**
 * Returns a default Realm write dispatcher for a Realm opened on the calling thread.
 */
public expect fun singleThreadDispatcher(id: String): CloseableCoroutineDispatcher

/**
 * Returns a default multithread dispatcher used by Sync.
 * TODO https://github.com/realm/realm-kotlin/issues/501 compute size based on number of cores
 */
public expect fun multiThreadDispatcher(size: Int = 3): CloseableCoroutineDispatcher

/**
 * Runs a new coroutine and **blocks** the current thread _interruptibly_ until its completion.
 *
 * This just exposes a common runBlocking for our supported platforms, as this is not available in
 * Kotlin's common packages due to lack of JS implementation.
 *
 * See documentation in one of the specific Kotlin implementations for further details.
 */
public expect fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T
