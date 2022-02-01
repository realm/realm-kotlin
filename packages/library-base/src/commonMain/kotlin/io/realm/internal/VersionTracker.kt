/*
 * Copyright 2022 Realm Inc.
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

package io.realm.internal

import io.realm.internal.platform.WeakReference
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

/**
 * Book keeping of intermediate versions that needs to be closed when no longer referenced or when
 * explicitly closing a realm.
 *
 * NOTE: This is not thread safe, so synchronization should be enforced by the owner/caller.
 */
internal class VersionTracker(val log: RealmLog) {
    // Set of currently open realms. Storing the native pointer explicitly to enable us to close
    // the realm when the RealmReference is no longer referenced anymore.
    private val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<RealmReference>>>> = atomic(mutableSetOf())

    fun trackNewAndCloseExpiredReferences(realmReference: FrozenRealmReference) {
        val references = mutableSetOf<Pair<NativePointer, WeakReference<RealmReference>>>(
            Pair(realmReference.dbPointer, WeakReference(realmReference))
        )
        intermediateReferences.value.forEach { entry ->
            val (pointer, ref) = entry
            if (ref.get() == null) {
                log.debug("Closing unreferenced version: ${RealmInterop.realm_get_version_id(pointer)}")
                RealmInterop.realm_close(pointer)
            } else {
                references.add(entry)
            }
        }
        intermediateReferences.value = references
    }

    fun close() {
        intermediateReferences.value.forEach { (pointer, _) ->
            log.debug(
                "Closing intermediated version: ${RealmInterop.realm_get_version_id(pointer)}"
            )
            RealmInterop.realm_close(pointer)
        }
    }
}
