package io.realm.internal.platform

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> T
): T {
    return kotlinx.coroutines.runBlocking(context, block)
}

/**
 * The default dispatcher for Darwin platforms spawns a new thread with a run loop.
 */
actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    return newSingleThreadContext(id)
}

actual fun multiThreadDispatcher(size: Int): CoroutineDispatcher {
    // TODO https://github.com/realm/realm-kotlin/issues/501
    return singleThreadDispatcher("singleThreadDispatcher")
}
