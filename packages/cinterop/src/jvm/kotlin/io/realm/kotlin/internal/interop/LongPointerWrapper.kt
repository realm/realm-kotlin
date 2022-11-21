/*
 * Copyright 2020 Realm Inc.
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

import io.realm.kotlin.internal.interop.gc.NativeContext
import java.lang.Long.toHexString
import java.util.concurrent.atomic.AtomicBoolean

// JVM/Android specific pointer wrapper
// FIXME Should ideally be moved to jni-swig-stub as we would be able to use Swig to wrap/unwrap
//  all pointers going in and out of the JNI layer, handling transferring ownership, etc. But,
//  doing so currently renders Android studio unable to resolve the NativePointer from the
//  runtime-api mpp-module, which ruins IDE solving of the while type hierarchy around the
//  pointers, which makes in annoying to work with.
//  https://issuetracker.google.com/issues/174162078
public class LongPointerWrapper<T : CapiT>(ptr: Long, managed: Boolean = true) : NativePointer<T> {
    // Tracks whether or not the `ptr` has already been released
    internal val released = AtomicBoolean(false)
    private val _ptr: Long = ptr
    // The underlying native pointer
    internal val ptr: Long
        get() {
            return if (!released.get()) {
                _ptr
            } else {
                throw POINTER_DELETED_ERROR
            }
        }

    init {
        if (managed) {
            NativeContext.addReference(this)
        }
    }

    override fun release() {
        if (released.compareAndSet(false, true)) {
            realmc.realm_release(_ptr)
        }
    }

    override fun isReleased(): Boolean = released.get()

    override fun toString(): String {
        return toHexString(_ptr)
    }
}
