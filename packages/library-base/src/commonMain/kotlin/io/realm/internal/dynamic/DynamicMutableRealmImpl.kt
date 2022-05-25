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

import io.realm.BaseRealmObject
import io.realm.Deleteable
import io.realm.UpdatePolicy
import io.realm.dynamic.DynamicMutableRealm
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.internal.BaseRealmImpl
import io.realm.internal.InternalConfiguration
import io.realm.internal.LiveRealmReference
import io.realm.internal.WriteTransactionManager
import io.realm.internal.asInternalDeleteable
import io.realm.internal.interop.LiveRealmPointer
import io.realm.internal.query.ObjectQuery
import io.realm.internal.runIfManaged
import io.realm.internal.toRealmObject
import io.realm.isValid
import io.realm.query.RealmQuery

internal open class DynamicMutableRealmImpl(
    configuration: InternalConfiguration,
    dbPointer: LiveRealmPointer
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

    // Type system doesn't prevent copying embedded objects, but theres not really a good way to
    // differentiate the dynamic objects without bloating the type space
    override fun copyToRealm(
        obj: BaseRealmObject,
        updatePolicy: UpdatePolicy
    ): DynamicMutableRealmObject {
        return io.realm.internal.copyToRealm(configuration.mediator, realmReference, obj, updatePolicy, mutableMapOf()) as DynamicMutableRealmObject
    }

    // This implementation should be aligned with InternalMutableRealm to ensure that we have same
    // semantics/error reporting
    override fun findLatest(obj: BaseRealmObject): DynamicMutableRealmObject? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == realmReference) {
                    obj as DynamicMutableRealmObject?
                } else {
                    return thaw(realmReference, DynamicMutableRealmObject::class)
                        ?.toRealmObject() as DynamicMutableRealmObject?
                }
            } ?: throw IllegalArgumentException("Cannot lookup unmanaged object")
        }
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }
}
