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

package io.realm.kotlin.internal.dynamic

import io.realm.kotlin.Deleteable
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.internal.BaseRealmImpl
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.LiveRealmReference
import io.realm.kotlin.internal.WriteTransactionManager
import io.realm.kotlin.internal.asInternalDeleteable
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.internal.runIfManaged
import io.realm.kotlin.internal.toRealmObject
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.schema.RealmClass

// Public due to tests needing to access `close` and trying to make the class visible through
// annotations didn't work for some reason.
public open class DynamicMutableRealmImpl(
    configuration: InternalConfiguration,
    dbPointer: LiveRealmPointer
) :
    BaseRealmImpl(configuration),
    DynamicMutableRealm,
    WriteTransactionManager {

    internal constructor(
        configuration: InternalConfiguration,
        realm: Pair<LiveRealmPointer, Boolean>
    ) : this(configuration, realm.first)

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
        obj: DynamicRealmObject,
        updatePolicy: UpdatePolicy
    ): DynamicMutableRealmObject {
        return io.realm.kotlin.internal.copyToRealm(configuration.mediator, realmReference, obj, updatePolicy, mutableMapOf()) as DynamicMutableRealmObject
    }

    // This implementation should be aligned with InternalMutableRealm to ensure that we have same
    // semantics/error reporting
    override fun findLatest(obj: DynamicRealmObject): DynamicMutableRealmObject? {
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

    override fun delete(className: String) {
        delete(query(className).find())
    }

    override fun deleteAll() {
        for (schemaClass: RealmClass in schema().classes) {
            delete(schemaClass.name)
        }
    }

    public override fun close() {
        super.close()
    }
}
