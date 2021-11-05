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

import kotlin.reflect.KType

// We could actually create `object` for all allowed types if needed
interface RealmPropertyType {
    // This doesn't necessarily catch Map<K,V> when/if we open up for K!=String, but the type is at
    // least encapsulated in RealmPropertyType, so should be able to change it later
    val collectionType: CollectionType
    val fieldType: FieldType
    val required: Boolean
    companion object {
        fun fromKotlinType(kType: KType) : RealmPropertyType { ... }
        // Maybe not possible as KType is somehow static altenatively to KClass<*> but that will
        // lack nullability
        fun toKotlinType(type: RealmPropertyType) : KType { ... }
    }
}
