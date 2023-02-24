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

package io.realm.kotlin.dynamic

import io.realm.kotlin.Realm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.schema.RealmStorageType
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass

/**
 * A **dynamic realm object** gives access to the data of the realm objects through a generic string
 * based API instead of the conventional [Realm] API that only allows access through the properties
 * of the corresponding schema classes supplied in the configuration.
 */
public interface DynamicRealmObject : BaseRealmObject {
    /**
     * The type of the object.
     *
     * This will normally correspond to the name of a model class that is extending
     * [RealmObject] or [EmbeddedRealmObject].
     */
    public val type: String

    /**
     * Returns the value of a specific non-nullable value property.
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicRealmObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicRealmObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicRealmObject.getObject("objectField", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value. Must match the [RealmStorageType.kClass] of the
     *              property in the realm.
     * @param T the type of the value.
     * @return the property value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [RealmStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun <T : Any> getValue(propertyName: String, clazz: KClass<T>): T

    /**
     * Returns the value of a specific nullable value property.
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicRealmObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicRealmObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicRealmObject.getObject("objectField", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @param clazz the Kotlin class of the value.
     * @param T the type of the value.
     * @return the [RealmList] value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [RealmStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun <T : Any> getNullableValue(propertyName: String, clazz: KClass<T>): T?

    /**
     * Returns the value of an object property.
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different value
     * types:
     * ```
     * // Retrieve a nullable int from a 'nullableIntField' property
     * dynamicRealmObject.getNullableValue("nullableIntField", Int::class)
     *
     * // Retrieve a non-nullable int from an 'intField' property
     * dynamicRealmObject.getValue("intField", Int::class)
     *
     * // Retrieve an object from an `objectField' property
     * dynamicRealmObject.getObject("objectField", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the property to retrieve the value for.
     * @return the [RealmList] value.
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if `clazz` doesn't match the property's [RealmStorageType.kClass] or if trying to
     * retrieve collection properties.
     */
    public fun getObject(propertyName: String): DynamicRealmObject?

    /**
     * Returns the list of non-nullable value elements referenced by the property name as a
     * [RealmList].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicRealmObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicRealmObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicRealmObject.getObjectList("objectList", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [RealmList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getValueList(propertyName: String, clazz: KClass<T>): RealmList<T>

    /**
     * Returns the list of nullable elements referenced by the property name as a [RealmList].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicRealmObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicRealmObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicRealmObject.getObjectList("objectList", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @param clazz the Kotlin class of the list element type.
     * @param T the type of the list element type.
     * @return the referenced [RealmList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getNullableValueList(propertyName: String, clazz: KClass<T>): RealmList<T?>

    /**
     * Returns the list of objects referenced by the property name as a [RealmList].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different list
     * types:
     * ```
     * // Retrieve a list of nullable ints from a 'nullableIntList' property
     * dynamicRealmObject.getNullableValueList("nullableIntList", Int::class)
     *
     * // Retrieve a list of non-nullable ints from a 'intList' property
     * dynamicRealmObject.getValueList("intList", Int::class)
     *
     * // Retrieve a list of objects from an `objectList' property
     * // Object lists are ALWAYS non-nullable
     * dynamicRealmObject.getObjectList("objectList", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the list property to retrieve the list for.
     * @return the referenced [RealmList]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-list properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun getObjectList(propertyName: String): RealmList<out DynamicRealmObject>

    /**
     * Returns the set of non-nullable value elements referenced by the property name as a
     * [RealmSet].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicRealmObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicRealmObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicRealmObject.getObjectSet("objectSet", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @param clazz the Kotlin class of the set element type.
     * @param T the type of the set element type.
     * @return the referenced [RealmSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T>

    /**
     * Returns the set of nullable elements referenced by the property name as a [RealmSet].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicRealmObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicRealmObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicRealmObject.getObjectSet("objectSet", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @param clazz the Kotlin class of the set element type.
     * @param T the type of the set element type.
     * @return the referenced [RealmSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getNullableValueSet(propertyName: String, clazz: KClass<T>): RealmSet<T?>

    /**
     * Returns the set of objects referenced by the property name as a [RealmSet].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different set types:
     * ```
     * // Retrieve a set of nullable ints from a 'nullableIntSet' property
     * dynamicRealmObject.getNullableValueSet("nullableIntSet", Int::class)
     *
     * // Retrieve a set of non-nullable ints from a 'intSet' property
     * dynamicRealmObject.getValueSet("intSet", Int::class)
     *
     * // Retrieve a set of objects from an `objectSet' property
     * // Object sets are ALWAYS non-nullable
     * dynamicRealmObject.getObjectSet("objectSet", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the set property to retrieve the set for.
     * @return the referenced [RealmSet]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-set properties or if `clazz` doesn't match the
     * property's [RealmStorageType.kClass].
     */
    public fun getObjectSet(propertyName: String): RealmSet<out DynamicRealmObject>

    /**
     * Returns the dictionary of non-nullable value elements referenced by the property name as a
     * [RealmDictionary].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicRealmObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicRealmObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicRealmObject.getObjectDictionary("objectDictionary", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @param clazz the Kotlin class of the dictionary element type.
     * @param T the type of the dictionary element type.
     * @return the referenced [RealmDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T>

    /**
     * Returns the dictionary of nullable elements referenced by the property name as a
     * [RealmDictionary].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicRealmObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicRealmObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicRealmObject.getObjectDictionary("objectDictionary", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @param clazz the Kotlin class of the dictionary element type.
     * @param T the type of the dictionary element type.
     * @return the referenced [RealmDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [RealmStorageType.kClass].
     */
    public fun <T : Any> getNullableValueDictionary(
        propertyName: String,
        clazz: KClass<T>
    ): RealmDictionary<T?>

    /**
     * Returns the dictionary of objects referenced by the property name as a [RealmDictionary].
     *
     * The `class` argument must be the [KClass] of the [RealmStorageType] for the property.
     *
     * The following snippet outlines the different functions available for the different dictionary
     * types:
     * ```
     * // Retrieve a dictionary of nullable ints from a 'nullableIntDictionary' property
     * dynamicRealmObject.getNullableValueDictionary("nullableIntDictionary", Int::class)
     *
     * // Retrieve a dictionary of non-nullable ints from a 'intDictionary' property
     * dynamicRealmObject.getValueDictionary("intDictionary", Int::class)
     *
     * // Retrieve a dictionary of objects from an `objectDictionary' property
     * // Object dictionaries are ALWAYS nullable
     * dynamicRealmObject.getObjectDictionary("objectDictionary", DynamicRealmObject::class)
     * ```
     *
     * @param propertyName the name of the dictionary property to retrieve the dictionary for.
     * @return the referenced [RealmDictionary]
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non-dictionary properties or if `clazz` doesn't match
     * the property's [RealmStorageType.kClass].
     */
    public fun getObjectDictionary(propertyName: String): RealmDictionary<out DynamicRealmObject?>

    /**
     * Returns a backlinks collection referenced by the property name as a [RealmResults].
     *
     * @param propertyName the name of the backlinks property to retrieve for.
     * @return the referencing objects as a [RealmResults].
     * @throws IllegalArgumentException if the class doesn't contain a field with the specific
     * name, if trying to retrieve values for non backlinks properties.
     */
    public fun getBacklinks(propertyName: String): RealmResults<out DynamicRealmObject>
}

/**
 * Returns the value of a specific value property.
 *
 * Reified convenience wrapper of [DynamicRealmObject.getValue].
 */
public inline fun <reified T : Any> DynamicRealmObject.getValue(fieldName: String): T =
    this.getValue(fieldName, T::class)

/**
 * Returns the value of a specific nullable value property.
 *
 * Reified convenience wrapper of [DynamicRealmObject.getNullableValue].
 */
public inline fun <reified T : Any> DynamicRealmObject.getNullableValue(fieldName: String): T? =
    this.getNullableValue(fieldName, T::class)

/**
 * Returns the list referenced by the property name as a [RealmList].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getValueList].
 */
public inline fun <reified T : Any> DynamicRealmObject.getValueList(fieldName: String): RealmList<T> =
    this.getValueList(fieldName, T::class)

/**
 * Returns the list of nullable elements referenced by the property name as a [RealmList].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getNullableValueList].
 */
public inline fun <reified T : Any> DynamicRealmObject.getNullableValueList(fieldName: String): RealmList<T?> =
    this.getNullableValueList(fieldName, T::class)

/**
 * Returns the set referenced by the property name as a [RealmSet].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getValueSet].
 */
public inline fun <reified T : Any> DynamicRealmObject.getValueSet(fieldName: String): RealmSet<T> =
    this.getValueSet(fieldName, T::class)

/**
 * Returns the set of nullable elements referenced by the property name as a [RealmSet].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getNullableValueSet].
 */
public inline fun <reified T : Any> DynamicRealmObject.getNullableValueSet(fieldName: String): RealmSet<T?> =
    this.getNullableValueSet(fieldName, T::class)

/**
 * Returns the set referenced by the property name as a [RealmDictionary].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getValueSet].
 */
public inline fun <reified T : Any> DynamicRealmObject.getValueDictionary(fieldName: String): RealmDictionary<T> =
    this.getValueDictionary(fieldName, T::class)

/**
 * Returns the set of nullable elements referenced by the property name as a [RealmDictionary].
 *
 * Reified convenience wrapper of [DynamicRealmObject.getNullableValueSet].
 */
public inline fun <reified T : Any> DynamicRealmObject.getNullableValueDictionary(fieldName: String): RealmDictionary<T?> =
    this.getNullableValueDictionary(fieldName, T::class)
