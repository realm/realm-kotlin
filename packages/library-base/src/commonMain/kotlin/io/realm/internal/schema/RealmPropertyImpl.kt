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

import io.realm.internal.interop.PropertyInfo
import io.realm.schema.CollectionType
import io.realm.schema.ElementType
import io.realm.schema.RealmProperty
import io.realm.schema.RealmPropertyType

internal data class RealmPropertyImpl(
    override var name: String,
    override var type: RealmPropertyType,
    override var primaryKey: Boolean,
    override var index: Boolean
) : RealmProperty {

    companion object {
        fun fromCoreProperty(corePropertyImpl: PropertyInfo): RealmPropertyImpl {
            return with(corePropertyImpl) {
                RealmPropertyImpl(
                    name,
                    RealmPropertyType(
                        fromCoreCollectionType(collectionType),
                        ElementType(fromCorePropertyType(type), isNullable)
                    ),
                    isPrimaryKey,
                    isIndexed,
                )
            }
        }

        fun fromCorePropertyType(type: io.realm.internal.interop.PropertyType): ElementType.FieldType {
            return when (type) {
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_INT -> ElementType.FieldType.INT
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_BOOL -> ElementType.FieldType.BOOL
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_STRING -> ElementType.FieldType.STRING
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_OBJECT -> ElementType.FieldType.OBJECT
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_FLOAT -> ElementType.FieldType.FLOAT
                io.realm.internal.interop.PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> ElementType.FieldType.DOUBLE
                else -> error("Unknown type: $type")
            }
        }

        fun fromCoreCollectionType(type: io.realm.internal.interop.CollectionType): CollectionType {
            return when (type) {
                io.realm.internal.interop.CollectionType.RLM_COLLECTION_TYPE_NONE -> CollectionType.NONE
                io.realm.internal.interop.CollectionType.RLM_COLLECTION_TYPE_LIST -> CollectionType.LIST
                else -> error("Unknown type: $type")
            }
        }
    }
}
