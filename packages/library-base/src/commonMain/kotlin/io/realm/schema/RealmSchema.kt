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

interface RealmSchema {
    // Alternatively as Map<String, RealmClass>
    val classes: Set<RealmClass>
}

interface RealmClass {
    val name: String
    val primaryKey : RealmProperty?
    val embedded: Boolean
    // Alternatively as Map<String, RealmProperty>
    val properties: Set<RealmProperty>
}

interface RealmProperty {
    val name: String // val publicName: String or should we just support mappings already?
    val index: Boolean
    val primaryKey: Boolean
    val type: RealmPropertyType
    val required: Boolean
        get() = type.required
}

fun RealmProperty(name: String, type: KType, index: Boolean=false, primaryKey: Boolean=false): RealmProperty {
    return RealmPropertyImpl(name, RealmPropertyType.fromKotlinType(type), index, primaryKey)
}

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
// Should we try to avoid enums completely as introducing new ones breaks compatibility due to
// requirement of exhaustive `when`s. ... but exhaustive `when`s are also extremely useful to
// ensure test coverage, etc.
enum class CollectionType {
    // SINGULAR, SET, LIST, MAP
}
// In realm.h this is called PropertyType but this would overlap with RealmProperty.type?
// Core types?
enum class FieldType {
    // INT, BOOL, STRING, OBJECT, FLOAT, DOUBLE, ...
}

interface MutableRealmSchema : RealmSchema {
    override val classes: MutableSet<MutableRealmClass>
}
interface MutableRealmClass : RealmClass {
    override var name: String
    override val properties: Set<MutableRealmProperty>
}
interface MutableRealmProperty : RealmProperty {
    override var name: String
    override var required: Boolean
    override var index: Boolean
    override var primaryKey: Boolean
}

