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

/**
 * A **dynamic realm object** gives access the data of the realm objects through a generic string
 * based API instead of the conventional [Realm] API that only allows access through the properties
 * of the corresponding schema classes supplied in the configuration.
 */
interface DynamicRealmObject : RealmObject {
    /**
     * The name of the class in the realm object model.
     */
    val type: String

    /**
     * Returns the value of a specific value property.
     *
     * *NOTE:* This shouldn't be used for nullable properties. Use [getNullable] for those.
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value.
     * @param T the type of the value.
     * @return the property value.
     * @throws IllegalArgummentException if the class doesn't contain a field with the specific
     * name, or if trying to retrieve collection properties.
     * @throws ClassCastException if the field doesn't contain a field of the defined return type.
     */
    fun <T : Any> get(propertyName: String, clazz: KClass<T>): T

    /**
     * Returns the value of a specific nullable value property.
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value.
     * @param T the type of the value.
     * @return the [RealmList] value.
     * @throws IllegalArgummentException if the class doesn't contain a field with the specific
     * name, or if trying to retrieve collection properties.
     * @throws ClassCastException if the field doesn't contain a field of the defined return type.
     */
    fun <T : Any> getNullable(propertyName: String, clazz: KClass<T>): T?

    /**
     * Returns the list referenced by the property name as a [RealmList].
     *
     * *NOTE:* This shouldn't be used for list of nullable values. Use [getListOfNullable] for those.
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [RealmList]
     * @throws IllegalArgummentException if the class doesn't contain a field with the specific
     * name.
     */
    // FIXME EVALUATE We don't have distinction between mutable and immutable list so we API will allow to
    //  modify the resulting list ... even though it will fail
    fun <T : Any> getList(propertyName: String, clazz: KClass<T>): RealmList<T>

    /**
     * Returns the list of nullable elements referenced by the property name as a [RealmList].
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [RealmList]
     * @throws IllegalArgummentException if the class doesn't contain a field with the specific
     * name.
     */
    fun <T : Any> getListOfNullable(propertyName: String, clazz: KClass<T>): RealmList<T?>
}

/**
 * Returns the value of a specific value property.
 *
 * Reified convenience wrapper of [DynamicRealmObject.get].
 */
inline fun <reified T : Any> DynamicRealmObject.get(fieldName: String): T = this.get(fieldName, T::class)
/**
 * Returns the value of a specific nullable value property.
 *
 * Reified convenience wrapper of [DynamicRealmObject.getNullable].
 */
inline fun <reified T : Any> DynamicRealmObject.getNullable(fieldName: String): T? = this.getNullable(fieldName, T::class)

/**
 * Returns the list referenced by the property name as a [RealmList].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getList].
 */
inline fun <reified T : Any> DynamicRealmObject.getList(fieldName: String): RealmList<T> = this.getList(fieldName, T::class)
/**
 * Returns the list of nullable elements referenced by the property name as a [RealmList].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getListOfNullable].
 */
inline fun <reified T : Any> DynamicRealmObject.getListOfNullable(fieldName: String): RealmList<T?> = this.getListOfNullable(fieldName, T::class)
