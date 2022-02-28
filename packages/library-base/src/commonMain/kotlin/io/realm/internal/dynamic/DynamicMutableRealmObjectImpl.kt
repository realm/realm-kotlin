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

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.internal.RealmObjectHelper
import io.realm.internal.RealmObjectInternal

internal class DynamicMutableRealmObjectImpl : DynamicMutableRealmObject, DynamicRealmObjectImpl() {

    override fun getObject(propertyName: String): DynamicMutableRealmObject? {
        return getNullableValue(propertyName, DynamicMutableRealmObject::class)
    }

    override fun getObjectList(propertyName: String): RealmList<DynamicMutableRealmObject> {
        return getValueList(propertyName, DynamicMutableRealmObject::class)
    }

    override fun <T> set(propertyName: String, value: T): DynamicMutableRealmObject {
        when (value) {
            is RealmObject -> RealmObjectHelper.setObject(
                this,
                propertyName,
                value as RealmObjectInternal
            )
            else -> RealmObjectHelper.setValue(this, propertyName, value)
        }
        return this
    }
}
