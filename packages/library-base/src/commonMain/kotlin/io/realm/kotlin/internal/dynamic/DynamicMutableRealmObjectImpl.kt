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

import io.realm.kotlin.RealmList
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.internal.RealmObjectHelper

internal class DynamicMutableRealmObjectImpl : DynamicMutableRealmObject, DynamicRealmObjectImpl() {

    override fun getObject(propertyName: String): DynamicMutableRealmObject? {
        return getNullableValue(propertyName, DynamicMutableRealmObject::class)
    }

    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject> {
        return getValueList(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        // `io_realm_kotlin_objectReference` is not null, as DynamicMutableRealmObject are always managed
        val reference = this.io_realm_kotlin_objectReference!!
        RealmObjectHelper.dynamicSetValue(reference, propertyName, value)
        return this
    }
}
