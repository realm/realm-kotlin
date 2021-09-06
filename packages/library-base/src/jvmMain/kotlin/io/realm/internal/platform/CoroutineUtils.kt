package io.realm.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

public actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    // TODO Propagate id to the underlying thread
    return Executors.newSingleThreadExecutor().asCoroutineDispatcher()
}

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}
