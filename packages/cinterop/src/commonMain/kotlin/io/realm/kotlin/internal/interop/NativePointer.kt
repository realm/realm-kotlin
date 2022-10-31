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

// Marker interface for native pointer wrappers
interface NativePointer<T : CapiT>

/**
 * Wrapper around NativePointers. This is required because we need to track if a NativePointer
 * has already been released. Ideally this should be merged into the [NativePointer], but due
 * to how phantom references work on the JVM this is not possible.
 *
 * I.e. when we wrap the NativePointer in a PhantomReference, there is no way to access it when it
 * is being GC'ed. So this mutable state is stored inside the NativePointerHolder which can be
 * stored safely as part of a phantom reference.
 *
 * This class is an implementation detail, that should only be used by either `LongPointerWrapper`
 * or `CPointerWrapper`.
 */
internal expect class NativePointerHolder {
    /**
     * Release the pointer contained in this holder. The pointer will only be released the first
     * time this method is called. Calling it multiple times is allowed, but will be a no-op.
     */
    fun release()
}
