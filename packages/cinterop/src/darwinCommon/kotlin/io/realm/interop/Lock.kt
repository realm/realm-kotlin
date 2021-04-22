/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.realm.interop

import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import realm_wrapper.ktor_mutex_create
import realm_wrapper.ktor_mutex_destroy
import realm_wrapper.ktor_mutex_lock
import realm_wrapper.ktor_mutex_t
import realm_wrapper.ktor_mutex_unlock
import kotlin.native.concurrent.freeze

/**
 * Source: https://github.com/ktorio/ktor/blob/main/ktor-utils/posix/src/io/ktor/util/LockNative.kt
 */
public actual class Lock {
    private val mutex = nativeHeap.alloc<ktor_mutex_t>()

    init {
        freeze()
        ktor_mutex_create(mutex.ptr).checkResult { "Failed to create mutex." }
    }

    public actual fun lock() {
        ktor_mutex_lock(mutex.ptr).checkResult { "Failed to lock mutex." }
    }

    public actual fun unlock() {
        ktor_mutex_unlock(mutex.ptr).checkResult { "Failed to unlock mutex." }
    }

    public actual fun close() {
        ktor_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex)
    }
}

private inline fun Int.checkResult(block: () -> String) {
    check(this == 0, block)
}
