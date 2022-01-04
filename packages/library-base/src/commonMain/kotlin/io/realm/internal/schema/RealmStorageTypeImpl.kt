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

package io.realm.internal.schema

import io.realm.schema.RealmStorageType

object RealmStorageTypeImpl {
    fun fromCorePropertyType(type: io.realm.internal.interop.PropertyType): RealmStorageType {
        return when (type) {
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_INT -> RealmStorageType.INT
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_BOOL -> RealmStorageType.BOOL
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_STRING -> RealmStorageType.STRING
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_OBJECT -> RealmStorageType.OBJECT
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_FLOAT -> RealmStorageType.FLOAT
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> RealmStorageType.DOUBLE
            io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> RealmStorageType.TIMESTAMP
            else -> error("Unknown storage type: $type")
        }
    }
}
