package io.realm.internal.worker

import android.os.Handler
import android.os.HandlerThread
import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.android.asCoroutineDispatcher
import java.util.concurrent.atomic.AtomicBoolean

actual fun getTaskThread(configuration: RealmConfiguration): TaskThread {
    return AndroidTaskThread(configuration)
}

class AndroidTaskThread(val configuration: RealmConfiguration)
    : HandlerThread("RealmNotifierLooper[${configuration.path}]"), TaskThread {

    private val handler: Handler
    private var started = AtomicBoolean(false)
    override val dispatcher: CoroutineDispatcher

    init {
        start()
        handler = Handler(looper) // Looper isn't available until Thread has been started.
        dispatcher = handler.asCoroutineDispatcher()
    }

    override fun startThread() {
        // Already started
        started.set(true)
    }

    override fun closeThread() {
        looper.quit()
        started.set(false)
    }

    override fun sendMessage(task: Runnable) {
        handler.post(task)
    }
}