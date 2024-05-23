/*
 * Copyright 2024 Realm Inc.
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
import io.realm.kotlin.schema.ListPropertyType
import io.realm.kotlin.schema.MapPropertyType
import io.realm.kotlin.schema.RealmPropertyType
import io.realm.kotlin.schema.SetPropertyType
import io.realm.kotlin.schema.ValuePropertyType

public val RealmPropertyType.collectionType: CollectionType
    get() {
        return when (this) {
            is ListPropertyType -> CollectionType.RLM_COLLECTION_TYPE_LIST
            is MapPropertyType -> CollectionType.RLM_COLLECTION_TYPE_DICTIONARY
            is SetPropertyType -> CollectionType.RLM_COLLECTION_TYPE_SET
            is ValuePropertyType -> CollectionType.RLM_COLLECTION_TYPE_NONE
        }
    }
