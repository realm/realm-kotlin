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

import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmProperty
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KMutableProperty1

internal data class RealmPropertyImpl(
    override var name: String,
    override var type: RealmPropertyType,
    override val inDataModel: Boolean,
) : RealmProperty {

    override val isNullable: Boolean = when (type) {
        is ValuePropertyType -> type.isNullable
        is ListPropertyType -> false
        is SetPropertyType -> false
        is MapPropertyType -> false
    }

    override val accessor: KMutableProperty1<out BaseRealmObject, *>?
        get() = null

    companion object {
        fun fromCoreProperty(corePropertyImpl: PropertyInfo, inModel: Boolean): RealmPropertyImpl {
            return with(corePropertyImpl) {
                val storageType = RealmStorageTypeImpl.fromCorePropertyType(type)
                val type = when (collectionType) {
                    CollectionType.RLM_COLLECTION_TYPE_NONE -> ValuePropertyType(
                        storageType,
                        isNullable,
                        isPrimaryKey,
                        isIndexed,
                        isFullTextIndexed
                    )
                    CollectionType.RLM_COLLECTION_TYPE_LIST -> ListPropertyType(
                        storageType,
                        isNullable,
                        isComputed
                    )
                    CollectionType.RLM_COLLECTION_TYPE_SET -> SetPropertyType(
                        storageType,
                        isNullable
                    )
                    CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> MapPropertyType(
                        storageType,
                        isNullable
                    )
                    else -> error("Unsupported type $collectionType")
                }
                RealmPropertyImpl(name, type, inModel)
            }
        }
    }
}
