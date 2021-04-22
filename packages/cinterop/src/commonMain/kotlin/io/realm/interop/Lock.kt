/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package io.realm.interop

public inline fun <R> Lock.withLock(crossinline block: () -> R): R {
    try {
        acquire()
        return block()
    } finally {
        release()
    }
}

/**
 * Platform independent lock making it possible to synchronize access to a resource.
 */
public expect class Lock {
    public fun acquire()
    public fun release()
    public fun close()
}
