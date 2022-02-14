/*
 * Copyright 2022 Realm Inc.
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

package io.realm

import kotlin.reflect.KClass

interface DynamicRealmObject : RealmObject {
    val type : String
    // FIXME Should we have something like
    //  val fields: Set<String>
    //  to ease access or is it ok to rely on realm.schema to introspect

    fun <T : Any> get(fieldName: String, clazz: KClass<T>): T
    fun <T : Any> getNullable(fieldName: String, clazz: KClass<T>): T?

    fun <T : Any> getList(fieldName: String, clazz: KClass<T>): RealmList<T>
    fun <T : Any> getListOfNullable(fieldName: String, clazz: KClass<T>): RealmList<T?>
}

inline fun <reified T : Any> DynamicRealmObject.get(fieldName: String): T = this.get(fieldName, T::class)
inline fun <reified T : Any> DynamicRealmObject.getNullable(fieldName: String): T? = this.getNullable(fieldName, T::class)

inline fun <reified T : Any> DynamicRealmObject.getList(fieldName: String): RealmList<T> = this.getList(fieldName, T::class)
inline fun <reified T : Any> DynamicRealmObject.getListOfNullable(fieldName: String): RealmList<T?> = this.getListOfNullable(fieldName, T::class)

