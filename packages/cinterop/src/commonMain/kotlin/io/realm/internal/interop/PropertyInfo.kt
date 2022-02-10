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

import io.realm.internal.interop.PropertyFlags.RLM_PROPERTY_INDEXED
import io.realm.internal.interop.PropertyFlags.RLM_PROPERTY_NORMAL
import io.realm.internal.interop.PropertyFlags.RLM_PROPERTY_NULLABLE
import io.realm.internal.interop.PropertyFlags.RLM_PROPERTY_PRIMARY_KEY

@Suppress("LongParameterList")
// TODO OPTIMIZE We could hold on to the native allocated memory and only read values lazily
//  This would avoid transferring anything not need. A better option would probably be to
//  implement as custom serializer, so that we could transfer the full struct in one bridge crossing.
data class PropertyInfo( // Kotlin variant of realm_property_info
    val name: String,
    val publicName: String = SCHEMA_NO_VALUE,
    val type: PropertyType,
    val collectionType: CollectionType = CollectionType.RLM_COLLECTION_TYPE_NONE,
    val linkTarget: String = SCHEMA_NO_VALUE,
    val linkOriginPropertyName: String = SCHEMA_NO_VALUE,
    val key: PropertyKey = INVALID_PROPERTY_KEY,
    val flags: Int = RLM_PROPERTY_NORMAL
) {
    val isNullable: Boolean = flags and PropertyFlags.RLM_PROPERTY_NULLABLE != 0
    val isPrimaryKey: Boolean = flags and PropertyFlags.RLM_PROPERTY_PRIMARY_KEY != 0
    val isIndexed: Boolean = flags and PropertyFlags.RLM_PROPERTY_INDEXED != 0

    companion object {
        // Convenience wrapper to ease maintaining compiler plugin
        fun create(
            name: String,
            publicName: String?,
            type: PropertyType,
            collectionType: CollectionType,
            linkTarget: String?,
            linkOriginPropertyName: String?,
            isNullable: Boolean,
            isPrimaryKey: Boolean,
            isIndexed: Boolean
        ): PropertyInfo {
            val flags =
                (if (isNullable) RLM_PROPERTY_NULLABLE else 0) or (if (isPrimaryKey) RLM_PROPERTY_PRIMARY_KEY else 0) or (if (isIndexed) RLM_PROPERTY_INDEXED else 0)
            return PropertyInfo(
                name,
                publicName ?: SCHEMA_NO_VALUE,
                type,
                collectionType,
                linkTarget ?: SCHEMA_NO_VALUE,
                linkOriginPropertyName ?: SCHEMA_NO_VALUE,
                INVALID_PROPERTY_KEY,
                flags
            )
        }

    }
}
