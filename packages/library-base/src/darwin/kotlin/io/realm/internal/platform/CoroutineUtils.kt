package io.realm.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.newSingleThreadContext
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
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
actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    return newSingleThreadContext(id)
}

actual fun multiThreadDispatcher(size: Int): CoroutineDispatcher {
    // FIXME https://github.com/realm/realm-kotlin/issues/450
    return singleThreadDispatcher("singleThreadDispatcher")
}
