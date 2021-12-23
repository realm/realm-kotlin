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
import io.realm.internal.schema.CachedSchemaMetadata
import io.realm.internal.schema.RealmSchemaImpl
import io.realm.log.LogLevel
import kotlinx.coroutines.CoroutineDispatcher

/**
 * A live realm that can be updated and receive notifications on data and schema changes when
 * updated by other threads.
 *
 * NOTE: Must be constructed with a single thread dispatch and must be constructed on the same
 * thread that is backing the dispatcher.
 *
 * @param snapshotOwner The owner of the snapshot references of this realm
 * @param configuration The configuration of the realm.
 * @param dispatcher The single thread dispatcher backing the realm scheduler of this realm. The
 * realm itself must only be access on the same thread.
 */
abstract class LiveRealm(val snapshotOwner: BaseRealmImpl, configuration: InternalConfiguration, dispatcher: CoroutineDispatcher? = null) : BaseRealmImpl(configuration, RealmInterop.realm_open(configuration.nativeConfig, dispatcher)) {

    private val realmChangeRegistration: RegistrationToken
    private val schemaChangeRegistraion: RegistrationToken

    var schema = CachedSchemaMetadata(realmReference.dbPointer)
        private set
    var snapshot: RealmReference = RealmReference(snapshotOwner, RealmInterop.realm_freeze(realmReference.dbPointer), schema)
        private set

    init {
        realmChangeRegistration = RealmInterop.realm_add_realm_changed_callback(realmReference.dbPointer, ::onRealmChanged)
        schemaChangeRegistraion = RealmInterop.realm_add_schema_changed_callback(realmReference.dbPointer, ::onSchemaChanged)
    }

    protected open fun onRealmChanged() {
        log.debug("Realm changed: $this $configuration")
        this.snapshot = RealmReference(snapshotOwner, RealmInterop.realm_freeze(realmReference.dbPointer), schema)
    }

    protected open fun onSchemaChanged(schema: NativePointer) {
        if (log.logLevel >= LogLevel.DEBUG) {
            log.debug("onSchemaChanged: $this $configuration ${RealmSchemaImpl.fromRealm(realmReference.dbPointer)}")
        } else {
            log.debug("onSchemaChanged: $this $configuration")
        }
        // realmReference.dbPointer is pointing to the live db, so conceptually the same schema,
        // but creating new instances to clear the cache
        this.schema = CachedSchemaMetadata(realmReference.dbPointer)
        // We are not guaranteed that onRealmChanged is triggered after updating the schema so update snapshot
        this.snapshot = RealmReference(snapshotOwner, RealmInterop.realm_freeze(realmReference.dbPointer), this.schema)

        // DEBUG
        // RealmInterop.realm_get_schema(realmReference.dbPointer)
    }

    override fun close() {
        RealmInterop.realm_remove_realm_changed_callback(realmReference.dbPointer, realmChangeRegistration)
        RealmInterop.realm_remove_schema_changed_callback(realmReference.dbPointer, schemaChangeRegistraion)
        super.close()
    }
}
