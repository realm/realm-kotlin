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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.schema.RealmClassImpl
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

// TODO MEDIATOR/API-INTERNAL Consider adding type parameter for the class

@Suppress("VariableNaming")
internal interface RealmObjectCompanion {
    val `io_realm_kotlin_class`: KClass<out TypedRealmObject>
    val `io_realm_kotlin_className`: String
    val `io_realm_kotlin_fields`: Map<String, KProperty1<BaseRealmObject, Any?>>
    val `io_realm_kotlin_primaryKey`: KMutableProperty1<*, *>?
    val `io_realm_kotlin_isEmbedded`: Boolean
    fun `io_realm_kotlin_schema`(): RealmClassImpl
    fun `io_realm_kotlin_newInstance`(): Any
}
