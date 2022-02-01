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

package io.realm.internal

import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RegistrationToken
import io.realm.internal.schema.RealmSchemaImpl
import io.realm.internal.util.Validation.sdkError
import io.realm.log.LogLevel
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A live realm that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatcher and must be constructed on the same
 * thread that is backing the dispatcher.
 *
 * @param owner The owner of the snapshot references of this realm.
 * @param configuration The configuration of the realm.
 * @param dispatcher The single thread dispatcher backing the realm scheduler of this realm. The
 * realm itself must only be access on the same thread.
 */
internal abstract class LiveRealm(val owner: RealmImpl, configuration: InternalConfiguration, dispatcher: CoroutineDispatcher? = null) : BaseRealmImpl(configuration) {

    private val realmChangeRegistration: RegistrationToken
    private val schemaChangeRegistration: RegistrationToken

    internal val versionTracker = VersionTracker(owner.log)

    override val realmReference: LiveRealmReference by lazy {
        val dbPointer = RealmInterop.realm_open(configuration.nativeConfig, dispatcher)
        LiveRealmReference(this, dbPointer)
    }

    private val _snapshot: AtomicRef<RealmReference?> = atomic(null)
    internal val snapshot: RealmReference
        get() {
            if (_snapshot.value == null) {
                val snapshot = FrozenRealmReference(owner, RealmInterop.realm_freeze(realmReference.dbPointer), realmReference.schemaMetadata)
                versionTracker.trackNewAndCloseExpiredReferences(snapshot)
                _snapshot.value = snapshot
            }
            return _snapshot.value ?: sdkError("Snapshot should never be null")
        }

    init {
        log.debug("asdasdf")
        realmChangeRegistration = RealmInterop.realm_add_realm_changed_callback(realmReference.dbPointer, ::onRealmChanged)
        schemaChangeRegistration = RealmInterop.realm_add_schema_changed_callback(realmReference.dbPointer, ::onSchemaChanged)
    }

    protected open fun onRealmChanged() {
        log.debug("Realm changed: $this $configuration")
        try {
            _snapshot.value = null
        } catch (e: Exception) {
            log.debug("${e.message}")
        }
    }

    protected open fun onSchemaChanged(schema: NativePointer) {
        if (log.logLevel >= LogLevel.DEBUG) {
            log.debug("onSchemaChanged: $this $configuration ${RealmSchemaImpl.fromRealm(realmReference.dbPointer)}")
        } else {
            log.debug("onSchemaChanged: $this $configuration")
        }
        realmReference.refreshSchema()
    }

    internal fun unregisterCallbacks() {
        RealmInterop.realm_remove_realm_changed_callback(realmReference.dbPointer, realmChangeRegistration)
        RealmInterop.realm_remove_schema_changed_callback(realmReference.dbPointer, schemaChangeRegistration)
    }

    override fun close() {
        unregisterCallbacks()
        versionTracker.close()
        super.close()
    }
}
