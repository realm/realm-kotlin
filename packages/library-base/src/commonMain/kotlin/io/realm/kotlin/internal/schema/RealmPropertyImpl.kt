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

import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType

internal data class RealmPropertyImpl(
    override var name: String,
    override var type: RealmPropertyType,
) : RealmProperty {

    override val isNullable: Boolean = when (type) {
        is ValuePropertyType -> type.isNullable
        is ListPropertyType -> false
        is SetPropertyType -> false // TODO is it allowed to be nullable?
    }

    companion object {
        fun fromCoreProperty(corePropertyImpl: PropertyInfo): RealmPropertyImpl {
            return with(corePropertyImpl) {
                val storageType =
                    io.realm.kotlin.internal.schema.RealmStorageTypeImpl.fromCorePropertyType(type)
                val type = when (collectionType) {
                    io.realm.kotlin.internal.interop.CollectionType.RLM_COLLECTION_TYPE_NONE -> ValuePropertyType(
                        storageType,
                        isNullable,
                        isPrimaryKey,
                        isIndexed
                    )
                    io.realm.kotlin.internal.interop.CollectionType.RLM_COLLECTION_TYPE_LIST -> ListPropertyType(
                        storageType,
                        isNullable
                    )
                    io.realm.kotlin.internal.interop.CollectionType.RLM_COLLECTION_TYPE_SET -> SetPropertyType(
                        storageType,
                        isNullable
                    )
                    else -> error("Unsupported type $collectionType")
                }
                RealmPropertyImpl(name, type)
            }
        }
    }
}
