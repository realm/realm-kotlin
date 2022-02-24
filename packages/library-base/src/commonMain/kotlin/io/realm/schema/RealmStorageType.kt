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

package io.realm.schema

import io.realm.RealmInstant
import io.realm.RealmObject
import kotlin.reflect.KClass

/**
 * The various types that are used when storing the property values in the realm.
 *
 * @param kClass the default Kotlin class used to represent values of the storage type. For
 * non-object types, this is the type of the value return by [DynamicRealmObject.getValue].
 */
public enum class RealmStorageType(public val kClass: KClass<*>) {
    BOOL(Boolean::class),
    INT(Long::class),
    STRING(String::class),
    OBJECT(RealmObject::class),
    FLOAT(Float::class),
    DOUBLE(Double::class),
    TIMESTAMP(RealmInstant::class);
}
