package io.realm.interop

import java.util.concurrent.locks.ReentrantLock

/**
 * Source: https://github.com/ktorio/ktor/blob/main/ktor-utils/jvm/src/io/ktor/util/LockJvm.kt
 */
public actual class Lock {
    private val lock = ReentrantLock()

    public actual fun acquire() {
        lock.lock()
    }

    public actual fun release() {
        lock.unlock()
    }

    public actual fun close() {
    }
}
