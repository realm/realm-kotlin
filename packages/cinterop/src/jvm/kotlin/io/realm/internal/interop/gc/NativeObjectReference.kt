/*
 * Copyright 2021 Realm Inc.
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

package io.realm.internal.interop.gc

import io.realm.internal.interop.LongPointerWrapper
import io.realm.internal.interop.realmc
import java.lang.ref.PhantomReference
import java.lang.ref.ReferenceQueue

/**
 * This class is used for holding the reference to the native pointers present in NativeObjects.
 * This is required as phantom references cannot access the original objects for this value.
 * The phantom references will be stored in a double linked list to avoid the reference itself gets GCed. When the
 * referent get GCed, the reference will be added to the ReferenceQueue. Loop in the daemon thread will retrieve the
 * phantom reference from the ReferenceQueue then dealloc the referent and remove the reference from the double linked
 * list. See [FinalizerRunnable] for more implementation details.
 */
internal class NativeObjectReference(
    private val context: NativeContext,
    referent: LongPointerWrapper,
    referenceQueue: ReferenceQueue<in LongPointerWrapper>?
) :
    PhantomReference<LongPointerWrapper>(referent, referenceQueue) {

    private val ptr: Long = referent.ptr

    private var prev: NativeObjectReference? = null
    private var next: NativeObjectReference? = null

    companion object {
        private val referencePool = ReferencePool()
    }

    init {
        referencePool.add(this)
    }

    /**
     * To dealloc native resources.
     */
    fun cleanup() {
        synchronized(context) {
            realmc.realm_release(ptr)
        }
        // Remove the PhantomReference from the pool to free it.
        referencePool.remove(this)
    }

    // Linked list to keep the reference of the PhantomReference
    private class ReferencePool {
        var head: NativeObjectReference? = null

        @Synchronized
        fun add(ref: NativeObjectReference) {
            ref.prev = null
            ref.next = head
            if (head != null) {
                head!!.prev = ref
            }
            head = ref
        }

        @Synchronized
        fun remove(ref: NativeObjectReference) {
            val next = ref.next
            val prev = ref.prev
            ref.next = null
            ref.prev = null
            if (prev != null) {
                prev.next = next
            } else {
                head = next
            }
            if (next != null) {
                next.prev = prev
            }
        }
    }
}
