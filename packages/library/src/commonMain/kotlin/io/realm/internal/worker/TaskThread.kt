package io.realm.internal.worker

import io.realm.RealmConfiguration
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Runnable

// TODO: I expect a number of these methods. Maybe introduce a TypeFactory that contain all of them?
expect fun getTaskThread(configuration: RealmConfiguration): TaskThread

interface TaskThread {
    fun startThread()
    fun closeThread()
    fun sendMessage(task: Runnable)
    val dispatcher: CoroutineDispatcher // Dispatcher that can be used to send and receive tasks
}

