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

package io.realm.kotlin.internal.schema

import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.RealmInstantImpl
import io.realm.kotlin.internal.RealmUUIDImpl
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmUUID
import kotlin.reflect.KClass

internal object RealmStorageTypeImpl {
    fun fromCorePropertyType(type: io.realm.kotlin.internal.interop.PropertyType): RealmStorageType {
        return when (type) {
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_INT -> RealmStorageType.INT
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_BOOL -> RealmStorageType.BOOL
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_STRING -> RealmStorageType.STRING
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_BINARY -> RealmStorageType.BINARY
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_OBJECT -> RealmStorageType.OBJECT
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_FLOAT -> RealmStorageType.FLOAT
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> RealmStorageType.DOUBLE
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> RealmStorageType.TIMESTAMP
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID -> RealmStorageType.OBJECT_ID
            io.realm.kotlin.internal.interop.PropertyType.RLM_PROPERTY_TYPE_UUID -> RealmStorageType.UUID
            else -> error("Unknown storage type: $type")
        }
    }
}

internal fun <T : Any> KClass<T>.realmStorageType(): KClass<*> = when (this) {
    ObjectIdImpl::class -> ObjectId::class
    RealmUUIDImpl::class -> RealmUUID::class
    RealmInstantImpl::class -> RealmInstant::class
    DynamicRealmObject::class,
    DynamicUnmanagedRealmObject::class,
    DynamicMutableRealmObject::class ->
        BaseRealmObject::class
    else -> this
}
