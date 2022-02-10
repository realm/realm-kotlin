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

import io.realm.DynamicMutableRealm
import io.realm.internal.interop.NativePointer
import io.realm.DynamicMutableRealmObject
import io.realm.DynamicRealmObject
import io.realm.RealmObject
import io.realm.internal.query.ObjectQuery
import io.realm.query.RealmQuery

internal class DynamicMutableRealmImpl(configuration: InternalConfiguration, dbPointer: NativePointer) : BaseRealmImpl(configuration), DynamicMutableRealm {

    override val realmReference: RealmReference = LiveRealmReference(this, dbPointer)

    override fun query(className: String, query: String, vararg args: Any?): RealmQuery<DynamicMutableRealmObject> =
            ObjectQuery(realmReference, className, DynamicMutableRealmObject::class, configuration.mediator, null, query, *args)


    override fun createObject(type: String): DynamicMutableRealmObject =
        create(configuration.mediator, realmReference, DynamicMutableRealmObject::class, type)

    override fun createObject(type: String, primaryKey: Any?): DynamicMutableRealmObject {
        TODO("Not yet implemented")
    }

//    override fun createEmbeddedObject(type: String, parent: DynamicRealmObject, parentProperty: String) {
//        TODO("Not yet implemented")
//    }

    override fun findLatest(obj: RealmObject): DynamicMutableRealmObject? {
        TODO("Not yet implemented")
    }

//    override fun cancelWrite() {
//        TODO("Not yet implemented")
//    }

    override fun delete(obj: RealmObject) {
        TODO("Not yet implemented")
    }
}
