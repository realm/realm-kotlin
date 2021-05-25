package io.realm.util

import io.realm.internal.util.runBlocking
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex

class Latch {
    val c = Channel<Boolean>(1)

    fun release() {
        runBlocking {
            c.send(true)
        }
    }

    fun await() {
        runBlocking {
            c.receive()
            c.close()
        }
    }
}