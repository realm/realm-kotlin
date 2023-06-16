package io.realm.kotlin.internal.platform

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newFixedThreadPoolContext
import kotlin.coroutines.CoroutineContext

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
public actual fun singleThreadDispatcher(id: String): CloseableCoroutineDispatcher {
    // Work-around for https://github.com/Kotlin/kotlinx.coroutines/issues/3768
    return newFixedThreadPoolContext(1, id)
}

public actual fun multiThreadDispatcher(size: Int): CloseableCoroutineDispatcher {
    // TODO https://github.com/realm/realm-kotlin/issues/501
    return singleThreadDispatcher("singleThreadDispatcher")
}
