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

package io.realm.kotlin.schema

import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlin.reflect.KClass

/**
 * The various types that are used when storing the property values in the realm.
 *
 * @param kClass the default Kotlin class used to represent values of the storage type.
 */
public enum class RealmStorageType(public val kClass: KClass<*>) {
    /**
     * Storage type for properties of type [Boolean].
     */
    BOOL(Boolean::class),

    /**
     * Storage type for properties of type [Byte], [Char], [Short], [Int] and [Long].
     */
    INT(Long::class),

    /**
     * Storage type for properties of type [String].
     */
    STRING(String::class),

    /**
     * Storage type for properties of type [ByteArray].
     */
    BINARY(ByteArray::class),

    /**
     * Storage type for properties of type [RealmObject] or [EmbeddedRealmObject].
     */
    OBJECT(BaseRealmObject::class),

    /**
     * Storage type for properties of type [Float].
     */
    FLOAT(Float::class),

    /**
     * Storage type for properties of type [Double].
     */
    DOUBLE(Double::class),

    /**
     * Storage type for properties of type [RealmInstant].
     */
    TIMESTAMP(RealmInstant::class),

    /**
     * Storage type for properties of type [ObjectId].
     */
    OBJECT_ID(ObjectId::class),

    /**
     * Storage type for properties of type [RealmUUID].
     */
    UUID(RealmUUID::class);
}
