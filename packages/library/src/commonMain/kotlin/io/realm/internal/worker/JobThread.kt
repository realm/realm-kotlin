package io.realm.internal.worker

import io.realm.RealmConfiguration
import io.realm.interop.NativePointer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Super class for creating a worker thread with an internal message queue for accepting jobs.
 * This is essentially the same as Kotlins [Worker](https://kotlinlang.org/docs/native-concurrency.html#workers)
 */
abstract class JobThread(val configuration: RealmConfiguration) {

    protected val jobThread = getTaskThread(configuration)
    private var started: Boolean = false
    val dispatcher = jobThread.dispatcher

    /**
     * Start the background worker thread and prepare it for receiving tasks.
     */
    open fun start() {
        jobThread.startThread()
        started = true
    }

    /**
     * Gracefully close the NotifierThread and all underlying resources. The Task queue will finish handling any current
     * task before closing down. This class will not send any more notifications when this methods returns, but due to
     * delays in any message queues, they might not be delivered until afterwards.
     */
    open fun close() {
        started = false
        jobThread.closeThread()
    }

    private inline fun checkStarted() {
        if (!started) {
            throw IllegalStateException("Notifier thread has not been started yet.")
        }
    }
}