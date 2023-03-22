/*
 * Copyright 2023 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.internal.interop

import kotlinx.cinterop.alloc
import kotlinx.cinterop.free
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import realm_wrapper.native_pthread_mutex_create
import realm_wrapper.native_pthread_mutex_destroy
import realm_wrapper.native_pthread_mutex_lock
import realm_wrapper.native_pthread_mutex_t
import realm_wrapper.native_pthread_mutex_unlock
import kotlin.native.internal.createCleaner

// Inspired by https://github.com/ktorio/ktor/blob/1.2.x/ktor-utils/posix/src/io/ktor/util/LockNative.kt
actual class SynchronizableObject {
    private val mutex = nativeHeap.alloc<native_pthread_mutex_t>()

    @OptIn(ExperimentalStdlibApi::class)
    private val cleaner = createCleaner(mutex) {
        native_pthread_mutex_destroy(mutex.ptr)
        nativeHeap.free(mutex)
    }

    init {
        native_pthread_mutex_create(mutex.ptr).checkResult { "Failed to create mutex." }
    }

    fun lock() {
        native_pthread_mutex_lock(mutex.ptr).checkResult { "Failed to lock mutex." }
    }

    fun unlock() {
        native_pthread_mutex_unlock(mutex.ptr).checkResult { "Failed to unlock mutex." }
    }
}

private inline fun Int.checkResult(block: () -> String) {
    check(this == 0, block)
}

actual inline fun <R> synchronized(lock: SynchronizableObject, block: () -> R): R {
    try {
        lock.lock()
        return block()
    } finally {
        lock.unlock()
    }
}
