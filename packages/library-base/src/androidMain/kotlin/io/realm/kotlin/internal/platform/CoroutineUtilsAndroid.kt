package io.realm.kotlin.internal.platform

import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher

actual fun singleThreadDispatcher(id: String): CoroutineDispatcher {
    val thread = HandlerThread("RealmWriter[$id]")
    thread.start()
    return Handler(thread.looper).asCoroutineDispatcher()
}
