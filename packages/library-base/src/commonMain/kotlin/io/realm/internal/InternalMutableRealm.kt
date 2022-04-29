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

import io.realm.Deleteable
import io.realm.MutableRealm
import io.realm.RealmObject
import io.realm.BaseRealmObject
import io.realm.isValid
import kotlinx.coroutines.flow.Flow

internal interface InternalMutableRealm : MutableRealm {

    override val configuration: InternalConfiguration
    val realmReference: LiveRealmReference

    override fun <T : BaseRealmObject> findLatest(obj: T): T? {
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
        updatePolicy: MutableRealm.UpdatePolicy
    ): T {
        // Embedded: Not possible to add top level objects as embedded
        return copyToRealm(configuration.mediator, realmReference, instance, updatePolicy)
    }

    // FIXME Consider adding a delete-all along with query support
    //  https://github.com/realm/realm-kotlin/issues/64
    // fun <T : RealmModel> delete(clazz: KClass<T>)

    fun <T> registerObserver(t: Thawable<T>): Flow<T> {
        throw IllegalStateException("Changes to RealmResults cannot be observed during a write.")
    }

    override fun delete(deleteable: Deleteable) {
        deleteable.asInternalDeleteable().delete()
    }
}
