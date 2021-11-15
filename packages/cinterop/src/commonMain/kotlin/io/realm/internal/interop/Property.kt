/*
 * Copyright 2020 Realm Inc.
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

package io.realm.internal.interop

@Suppress("LongParameterList")
// TODO OPTIMIZE We could hold on to the native allocated memory and only read values lazily
//  This would avoid transferring anything not need. A better option would probably be to
//  implement as custom serializer, so that we could transfer the full struct in one bridge crossing.
data class Property( // Kotlin variant of realm_property_info
    val name: String,
    val publicName: String = "",
    val type: PropertyType,
    val collectionType: CollectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
    val linkTarget: String = "",
    val linkOriginPropertyName: String = "",
    val key: Long, // PropertyKey,
    val flags: Int
) {
    val isNullable: Boolean = flags and PropertyFlags.RLM_PROPERTY_NULLABLE != 0
    val isPrimaryKey: Boolean = flags and PropertyFlags.RLM_PROPERTY_PRIMARY_KEY != 0
    val isIndexed: Boolean = flags and PropertyFlags.RLM_PROPERTY_INDEXED != 0
}
