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
package io.realm.kotlin.internal

import io.realm.kotlin.Deleteable
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.types.AsymmetricRealmObject
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

internal interface InternalMutableRealm : MutableRealm {

    override val configuration: InternalConfiguration
    val realmReference: LiveRealmReference

    override fun <T : TypedRealmObject> findLatest(obj: T): T? {
        return if (!obj.isValid()) {
            null
        } else {
            obj.runIfManaged {
                if (owner == realmReference) {
                    // If already valid, managed and not frozen, it must be live, and thus already
                    // up to date, just return input
                    obj
                } else {
                    return thaw(realmReference)?.toRealmObject() as T?
                }
            } ?: throw IllegalArgumentException(
                "Unmanaged objects must be part of the Realm, before " +
                    "they can be queried this way. Use `MutableRealm.copyToRealm()` to turn it into " +
                    "a managed object."
            )
        }
    }

    override fun <T : RealmObject> copyToRealm(
        instance: T,
        updatePolicy: UpdatePolicy
    ): T {
        return copyToRealm(configuration.mediator, realmReference, instance, updatePolicy)
    }

    override fun <T : AsymmetricRealmObject> copyToRealm(
        instance: T,
    ) {
        // TODO What happens on primary key conflicts?
        copyToRealm(configuration.mediator, realmReference, instance, UpdatePolicy.ERROR)
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }

    override fun <T : TypedRealmObject> delete(schemaClass: KClass<T>) {
        try {
            delete(query(schemaClass).find())
        } catch (err: IllegalStateException) {
            if (err.message?.contains("not part of this configuration schema") == true) {
                throw IllegalArgumentException(err.message)
            } else {
                throw err
            }
        }
    }

    override fun deleteAll() {
        for (schemaClass: KClass<out BaseRealmObject> in configuration.schema) {
            // TODO This breaks the idea about exposing AsymmetricRealmObjects as a subclass
            //  of BaseRealmObject. The problem is that BaseRealmObject is marked Deletable.
            //  I guess we have 2 options: A) Either move the Deletable interface to all object
            //  relevant subclasses B) Decouple AsymmetricRealmObject completely from BaseRealmObject
            //  B) has some merit since you are only allowed to insert these objects, not query or
            //  modify them after creation. I do however suspect it will make inserting the objects
            //  much more difficult since it would change our compiler infrastructure quite a bit.
            //  This needs to be discussed.
            if (schemaClass.isInstance(Deleteable::class)) {
                delete(schemaClass as Deleteable)
            }
        }
    }
}
