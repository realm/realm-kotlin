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

internal val POINTER_DELETED_ERROR = IllegalStateException("Cannot perform this operation on an invalid/deleted object")

// Marker interface for native pointer wrappers
public interface NativePointer<T : CapiT> {
    /**
     * Delete the underlying native pointer. The pointer will only be deleted the first
     * time this method is called. Calling it multiple times is allowed, but will be a no-op.
     */
    public fun release()

    /**
     * Returns whether or not the pointer has been released.
     *
     * This is required as deleting the underlying pointer on JVM doesn't necessarily clean
     * the underlying native memory and thus accessing that memory is undefined.
     *
     * E.g. calling
     *
     * ```
     * realmc.realm_release(objPointer)
     * realmc.realm_object_is_valid(objPointer) // can return both true and false
     * ```
     *
     * is undefined behaviour, which can leak into the SDK in surprising ways.
     */
    public fun isReleased(): Boolean
}
