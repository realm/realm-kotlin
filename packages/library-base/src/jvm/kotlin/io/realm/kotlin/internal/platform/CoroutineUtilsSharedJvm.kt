package io.realm.kotlin.internal.platform

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

public actual fun multiThreadDispatcher(size: Int): CoroutineDispatcher =
    Executors.newFixedThreadPool(size).asCoroutineDispatcher()

public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}
