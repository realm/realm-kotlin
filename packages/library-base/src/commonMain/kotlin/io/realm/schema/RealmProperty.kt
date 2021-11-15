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

interface RealmProperty { // Matches realm_property_info_t
    val name: String // val publicName: String or should we just support mappings already?
    // Type holds, collection, field type and nullability
    // Maybe make this a data class, so it is easy to update as a single operation here
    // Maybe nullability should be exposed as separate property as it is flip-able while it is not
    // possible to update the types
    val type: RealmPropertyType
    val primaryKey: Boolean
    val index: Boolean
    // Derived attributes, maybe expose as top level attribute instead of hiding it inside type?
    val nullable: Boolean get() = type.elementType.nullable
}
