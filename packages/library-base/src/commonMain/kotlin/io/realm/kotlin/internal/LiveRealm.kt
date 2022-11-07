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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSchemaPointer
import io.realm.kotlin.internal.platform.WeakReference
import io.realm.kotlin.internal.util.Validation.sdkError
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A live realm that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatcher and must be constructed on the same
 * thread that is backing the dispatcher. Further, this is not thread safe so must only be modified
 * on the dispatcher's thread.
 *
 * @param owner The owner of the snapshot references of this realm.
 * @param configuration The configuration of the realm.
 * @param dispatcher The single thread dispatcher backing the realm scheduler of this realm. The
 * realm itself must only be access on the same thread.
 */
internal abstract class LiveRealm(val owner: RealmImpl, configuration: InternalConfiguration, dispatcher: CoroutineDispatcher? = null) : BaseRealmImpl(configuration) {

    private val realmChangeRegistration: NotificationToken
    private val schemaChangeRegistration: NotificationToken

    internal val versionTracker = VersionTracker(owner.log)

    override val realmReference: LiveRealmReference by lazy {
        val (dbPointer, fileCreated) = RealmInterop.realm_open(configuration.createNativeConfiguration(), dispatcher)
        LiveRealmReference(this, dbPointer)
    }

    private val _snapshot: AtomicRef<FrozenRealmReference?> = atomic(null)

    /**
     * Frozen snapshot reference of the realm.
     *
     * NOTE: The snapshot is lazily created and must only be retrieved on the thread of the
     * dispatcher.
     */
    internal val snapshot: FrozenRealmReference
        get() {
            // Initialize a new snapshot that can be reused until cleared again from onRealmChanged
            if (_snapshot.value == null) {
                println("Create snapshot from owner")
                val snapshot: FrozenRealmReference = realmReference.snapshot(owner)
                versionTracker.trackAndCloseExpiredReferences(snapshot)
                _snapshot.value = snapshot
            }
            return _snapshot.value ?: sdkError("Snapshot should never be null")
        }

    init {
        @Suppress("LeakingThis") // Should be ok as we do not rely on this to be fully initialized
        val callback = WeakLiveRealmCallback(this)
        realmChangeRegistration = NotificationToken(RealmInterop.realm_add_realm_changed_callback(realmReference.dbPointer, callback::onRealmChanged))
        schemaChangeRegistration = NotificationToken(RealmInterop.realm_add_schema_changed_callback(realmReference.dbPointer, callback::onSchemaChanged))
    }

    protected open fun onRealmChanged() {
        // Just clean snapshot so that a new one is initialized next time it is needed
        _snapshot.value = null
    }

    protected open fun onSchemaChanged(schema: RealmSchemaPointer) {
        realmReference.refreshSchemaMetadata()
    }

    internal fun unregisterCallbacks() {
        realmChangeRegistration.cancel()
        schemaChangeRegistration.cancel()
    }

    override fun close() {
        unregisterCallbacks()
        // Close all intermediate references
        versionTracker.close()
        // Close actual live reference
        realmReference.close()
        super.close()
    }

    private class WeakLiveRealmCallback(liveRealm: LiveRealm) {
        val realm: WeakReference<LiveRealm> = WeakReference(liveRealm)
        fun onRealmChanged() { realm.get()?.onRealmChanged() }
        fun onSchemaChanged(schema: RealmSchemaPointer) { realm.get()?.onSchemaChanged(schema) }
    }
}
