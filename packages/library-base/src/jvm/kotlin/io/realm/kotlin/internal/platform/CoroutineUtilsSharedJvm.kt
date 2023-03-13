package io.realm.kotlin.internal.platform

import kotlinx.coroutines.CloseableCoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

internal actual fun singleThreadDispatcher(id: String): CloseableCoroutineDispatcher {
    return Executors.newSingleThreadExecutor { action: Runnable ->
        Thread(action).apply {
            name = id
            priority = Thread.NORM_PRIORITY
        }
    }.asCoroutineDispatcher()
}

internal actual fun multiThreadDispatcher(size: Int): CloseableCoroutineDispatcher =
    Executors.newFixedThreadPool(size).asCoroutineDispatcher()

public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}
