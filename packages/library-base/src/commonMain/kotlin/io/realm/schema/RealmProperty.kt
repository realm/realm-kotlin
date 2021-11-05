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

interface RealmProperty {
    val name: String // val publicName: String or should we just support mappings already?
    val index: Boolean
    val primaryKey: Boolean
    val type: RealmPropertyType
    val required: Boolean
        get() = type.required
    // Custom typemaps
}

fun RealmProperty(name: String, type: KType, index: Boolean=false, primaryKey: Boolean=false): RealmProperty {
    return RealmPropertyImpl(name, RealmPropertyType.fromKotlinType(type), index, primaryKey)
}

