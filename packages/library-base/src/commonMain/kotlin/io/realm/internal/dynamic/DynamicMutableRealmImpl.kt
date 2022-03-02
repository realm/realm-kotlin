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

package io.realm.internal.dynamic

import io.realm.Deleteable
import io.realm.RealmObject
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.internal.BaseRealmImpl
import io.realm.internal.InternalConfiguration
import io.realm.internal.LiveRealmReference
import io.realm.internal.RealmObjectInternal
import io.realm.internal.WriteTransactionManager
import io.realm.internal.asInternalDeleteable
import io.realm.internal.create
import io.realm.internal.interop.NativePointer
import io.realm.internal.query.ObjectQuery
import io.realm.isManaged
import io.realm.isValid
import io.realm.query.RealmQuery

internal open class DynamicMutableRealmImpl(
    configuration: InternalConfiguration,
    dbPointer: NativePointer
) :
    BaseRealmImpl(configuration),
    DynamicMutableRealm,
    WriteTransactionManager {

    override val realmReference: LiveRealmReference = LiveRealmReference(this, dbPointer)

    override fun query(
        className: String,
        query: String,
        vararg args: Any?
    ): RealmQuery<DynamicMutableRealmObject> =
        ObjectQuery(
            realmReference,
            realmReference.schemaMetadata.getOrThrow(className).classKey,
            DynamicMutableRealmObject::class,
            configuration.mediator,
            null,
            query,
            *args
        )

    override fun createObject(type: String): DynamicMutableRealmObject =
        create(configuration.mediator, realmReference, DynamicMutableRealmObject::class, type)

    override fun createObject(type: String, primaryKey: Any?): DynamicMutableRealmObject =
        create(
            configuration.mediator,
            realmReference,
            DynamicMutableRealmObject::class,
            type,
            primaryKey
        )

    // This implementation should be aligned with InternalMutableRealm to ensure that we have same
    // semantics/error reporting
    override fun findLatest(obj: RealmObject): DynamicMutableRealmObject? {
        return if (!obj.isValid()) {
            null
        } else if (!obj.isManaged()) {
            throw IllegalArgumentException("Cannot lookup unmanaged object")
        } else if ((obj as RealmObjectInternal).`$realm$Owner` == realmReference) {
            obj as DynamicMutableRealmObject?
        } else {
            obj.thaw(realmReference, DynamicMutableRealmObject::class) as DynamicMutableRealmObject?
        }
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }
}
