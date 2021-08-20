package io.realm.internal.platform

import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlin.coroutines.CoroutineContext

actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    val thread = HandlerThread("RealmWriter[$id]")
    thread.start()
    return Handler(thread.looper).asCoroutineDispatcher()
}

// Expose platform runBlocking through common interface
public actual fun <T> runBlocking(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T {
    return kotlinx.coroutines.runBlocking(context, block)
}
