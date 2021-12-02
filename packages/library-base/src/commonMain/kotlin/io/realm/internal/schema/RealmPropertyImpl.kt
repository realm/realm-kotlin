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
import io.realm.schema.ListPropertyType
import io.realm.schema.RealmProperty
import io.realm.schema.RealmPropertyType
import io.realm.schema.ValuePropertyType

internal data class RealmPropertyImpl(
    override var name: String,
    override var type: RealmPropertyType,
) : RealmProperty {

    override val isNullable: Boolean = when (type) {
        is ValuePropertyType -> type.isNullable
        is ListPropertyType -> false
    }

    companion object {
        fun fromCoreProperty(corePropertyImpl: PropertyInfo): RealmPropertyImpl {
            return with(corePropertyImpl) {
                val storageType =
                    io.realm.internal.schema.RealmStorageTypeImpl.fromCorePropertyType(type)
                val type = when (collectionType) {
                    io.realm.internal.interop.CollectionType.RLM_COLLECTION_TYPE_NONE -> SingularPropertyType(
                        storageType,
                        isNullable,
                        isPrimaryKey,
                        isIndexed
                    )
                    io.realm.internal.interop.CollectionType.RLM_COLLECTION_TYPE_LIST -> ListPropertyType(
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
