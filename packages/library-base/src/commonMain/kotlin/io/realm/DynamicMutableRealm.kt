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

package io.realm

import io.realm.internal.query.ObjectQuery
import io.realm.query.RealmQuery

// Or MigrationRealm
interface DynamicMutableRealm: DynamicRealm {

    override fun query(clazz: String, query: String, vararg args: Any?): RealmQuery<DynamicMutableRealmObject>

    fun createObject(type: String): DynamicMutableRealmObject
    fun createObject(type: String, primaryKey: Any?): DynamicMutableRealmObject
//    fun createEmbeddedObject(type: String, parent: DynamicRealmObject, parentProperty: String)
    fun findLatest(obj: RealmObject): DynamicMutableRealmObject?
//    fun cancelWrite()
    fun delete(obj: RealmObject)
}
