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

package io.realm.internal

import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.interop.CollectionType
import io.realm.internal.interop.RealmValue
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCorePropertyNotNullableException
import io.realm.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmListPointer
import io.realm.internal.schema.RealmStorageTypeImpl
import io.realm.schema.RealmStorageType
import kotlin.reflect.KClass



/**
 * This object holds helper methods for the compiler plugin generated methods, providing the
 * convenience of writing manually code instead of adding it through the compiler plugin.
 *
 * Inlining would anyway yield the same result as generating it.
 */
internal object RealmObjectHelper {
    // Issues (not yet fully uncovered/filed) met when calling these or similar methods from
    // generated code
    // - Generic return type should be R but causes compilation errors for native
    //  e: java.lang.IllegalStateException: Not found Idx for public io.realm.internal/RealmObjectHelper|null[0]/
    // - Passing KProperty1<T,R> with inlined reified type parameters to enable fetching type and
    //   property names directly from T/property triggers runtime crash for primitive properties on
    //   Kotlin native. Seems to be an issue with boxing/unboxing



    // IN
    // realm_object_find_with_primary_key
    // realm_object_get_or_create_with_primary_key
    // realm_set_value
    // realm_set_values
    // realm_list_set
    // realm_list_insert
    // realm_list_assign
    // realm_query_parse
    // realm_query_append_query
    // realm_query_parse_for_list
    // realm_query_parse_for_results

    // OUT
    // realm_get_value
    // realm_get_values
    // realm_list_get
    // realm_query_find_first returns object
    // realm_results_get

    // Property<UT, ST>

    internal inline fun <reified T : Any, reified S : Any> getValue(obj: RealmObjectReference<out RealmObject>, propertyName: String): Any? {
        return getValue2(
            obj,
            propertyName,

            defaultToPublicType(T::class),
        )
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    inline internal fun <T : Any, S> getValue2(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
        fromRealmValue: CinteropGetter<S>,
        toCustomType: CustomConverter<S, T> =
    ): Any? {
        obj.checkValid()
        val valueByKey = getValueByKey(obj, obj.propertyInfoOrThrow(propertyName).key)
        println("getValue RealmValue: $valueByKey")
        val cinteropGetter1: S? = fromRealmValue(valueByKey)
        println("getValue StorageType: $cinteropGetter1")
        return toCustomType(cinteropGetter1)
    }

    internal fun getValueByKey(
        obj: RealmObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
    ): RealmValue = RealmInterop.realm_get_value(obj.objectPointer, key)

    @Suppress("unused") // Called from generated code
    internal fun <R, U> getTimestamp(obj: RealmObjectReference<out RealmObject>, propertyName: String): RealmInstant? {
        return getValue<RealmInstant, RealmInstant>(obj, propertyName) as RealmInstant?
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject, U> getObject(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        converter(R::class, RealmStorageType.OBJECT, obj.mediator, obj.owner)
        // val x : CinteropGetter2<R> = DynamicConverter::realmValueToStorageType as CinteropGetter2<R>
        // val y : CustomGetter2<R, R> = DynamicConverter::fromStorageType as CustomGetter2<R, R>
        return getValue2<R, R>(obj, propertyName, y, R::class, x, R::class)
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    internal inline fun <reified R : Any, U> getList(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String
    ): ManagedRealmList<Any?> = getList<R,U>(obj, propertyName, R::class)

    internal fun <R, U> getList(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
        elementType: KClass<*>,
    ): ManagedRealmList<Any?> =
        getListByKey(obj, obj.propertyInfoOrThrow(propertyName).key, elementType)

    // Cannot call managedRealmList directly from an inline function
    internal fun <R> getListByKey(
        obj: RealmObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
        elementType: KClass<*>,
    ): ManagedRealmList<R> = getManagedRealmList(
        RealmInterop.realm_get_list(obj.objectPointer, key),
        elementType,
        obj.mediator,
        obj.owner
    )

    /**
     * Helper function that returns a managed list. This is needed due to the restriction of inline
     * functions not being able to access non-public API methods - managedRealmList is `internal`
     * and therefore it cannot be called from `getList`
     */
    internal fun <R> getManagedRealmList(
        listPtr: RealmListPointer,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference
    ): ManagedRealmList<R> {
        val converter: RealmConverter<R> = converter(clazz, RealmStorageType.BOOL, mediator, realm) as RealmConverter<R>
        val metadata: ListOperatorMetadata<R> = ListOperatorMetadata(
            mediator = mediator,
            realm = realm,
            converter,
        )
        return managedRealmList(listPtr, metadata)
    }


    internal fun <R> setValue(obj: RealmObjectReference<out RealmObject>, propertyName: String, value: R) {
        // println("setValue: $propertyName")
        setValue2(
            obj,
            propertyName,
            value,
            DynamicConverter::toStorageType,
            DynamicConverter::storageTypeToRealmValue
        )
    }
    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <T, S> setValue2(obj: RealmObjectReference<out RealmObject>, propertyName: String, value: T, customSetter2: CustomSetter2<T, S>, cinteropSetter: CinteropSetter<S>) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        // TODO OPTIMIZE We are currently only doing this check for typed access so could consider
        //  moving the guard into the compiler plugin. Await the implementation of a user
        //  facing general purpose dynamic realm (not only for migration) before doing this, as
        //  this would also require the guard ... or maybe await proper core support for throwing
        //  when this is not supported.
        obj.metadata.let { classMetaData ->
            val primaryKeyPropertyKey: PropertyKey? = classMetaData.primaryKeyPropertyKey
            if (primaryKeyPropertyKey != null && key == primaryKeyPropertyKey) {
                val name = classMetaData[primaryKeyPropertyKey]!!.name
                throw IllegalArgumentException("Cannot update primary key property '${obj.className}.$name'")
            }
        }
        val storageType: S? = customSetter2(value)
        val realmValue = cinteropSetter(storageType)
        setValueByKey(obj, key, realmValue)
    }

    internal fun setValueByKey(
        obj: RealmObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
        value: RealmValue,
    ) {
        try {
            // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
            //  implementations in the various platform RealmInterops here to eliminate
            //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
            //  only. This relates to the overall concern of having a generic path for getter/setter
            //  instead of generating a typed path for each type.
            RealmInterop.realm_set_value(obj.objectPointer, key, value, false)
            // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
            // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
            // info that might help users to understand the exception.
        } catch (exception: RealmCorePropertyNotNullableException) {
            throw IllegalArgumentException("Required property `${obj.className}.${obj.metadata[key]!!.name}` cannot be null")
        } catch (exception: RealmCorePropertyTypeMismatchException) {
            throw IllegalArgumentException("Property `${obj.className}.${obj.metadata[key]!!.name}` cannot be assigned with value '$value' of wrong type")
        } catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.className}.$${obj.metadata[key]!!.name}` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject> setObject(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
        value: R?
    ) {
        obj.checkValid()
        val realmReference = obj.owner.asValidLiveRealmReference()

        val newValue = value?.runIfManaged {
            if (obj.owner == owner) value else null
        } ?: copyToRealm(obj.mediator, realmReference, value)

        // setValueByKey(obj, obj.propertyInfoOrThrow(propertyName).key, newValue?.realmObjectReference)
        setValue(obj, propertyName, newValue?.realmObjectReference)
    }

    /**
     * Get values for non-collection properties by name.
     *
     * This will verify that the requested type (`clazz`) and nullability matches the property
     * properties in the schema.
     */
    internal fun <R : Any> dynamicGet(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): R? {
        obj.checkValid()
        val propertyInfo = checkPropertyType(obj, propertyName, CollectionType.RLM_COLLECTION_TYPE_NONE, clazz, nullable)
        val value = when (clazz) {
            RealmInstant::class -> getTimestamp<R, String>(obj, propertyName)
            DynamicRealmObject::class -> TODO()//getObjectByKey<DynamicRealmObject>(obj, propertyInfo.key)
            DynamicMutableRealmObject::class -> TODO()//getObjectByKey<DynamicMutableRealmObject>(
                // obj,
                // propertyInfo.key
            // )
            else -> getValueByKey(obj, propertyInfo.key)
        }
        return value?.let {
            @Suppress("UNCHECKED_CAST")
            if (clazz.isInstance(value)) {
                value as R?
            } else {
                throw ClassCastException("Retrieving value of type '${clazz.simpleName}' but was of type '${value::class.simpleName}'")
            }
        }
    }

    internal fun <R : Any> dynamicGetList(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): RealmList<R?> {
        obj.checkValid()
        val propertyInfo = checkPropertyType(obj, propertyName, CollectionType.RLM_COLLECTION_TYPE_LIST, clazz, nullable)
        @Suppress("UNCHECKED_CAST")
        // return getListByKey(obj, propertyInfo.key, clazz) as RealmList<R?>
        TODO()
    }

    private fun checkPropertyType(obj: RealmObjectReference<out RealmObject>, propertyName: String, collectionType: CollectionType, elementType: KClass<*>, nullable: Boolean): PropertyInfo {
        val realElementType = when (elementType) {
            DynamicRealmObject::class,
            DynamicMutableRealmObject::class ->
                RealmObject::class
            else -> elementType
        }

        return obj.metadata.getOrThrow(propertyName).also { propertyInfo ->
            val kClass = RealmStorageTypeImpl.fromCorePropertyType(propertyInfo.type).kClass
            if (collectionType != propertyInfo.collectionType ||
                realElementType != kClass ||
                nullable != propertyInfo.isNullable
            ) {
                throw IllegalArgumentException("Trying to access property '${obj.className}.$propertyName' as type: '${formatType(collectionType, realElementType, nullable)}' but actual schema type is '${formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)}'")
            }
        }
    }

    internal fun <R> dynamicSetValue(obj: RealmObjectReference<out RealmObject>, propertyName: String, value: R) {
        obj.checkValid()
        setValue(obj, propertyName, value)
    }

    private fun formatType(collectionType: CollectionType, elementType: KClass<*>, nullable: Boolean): String {
        val elementTypeString = elementType.toString() + if (nullable) "?" else ""
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> elementTypeString
            CollectionType.RLM_COLLECTION_TYPE_LIST -> "RealmList<$elementTypeString>"
            else -> TODO("Unsupported collection type: $collectionType")
        }
    }

    @Suppress("unused") // Called from generated code
    inline fun <reified T : Any> setList(obj: RealmObjectReference<out RealmObject>, col: String, list: RealmList<Any?>) {
        val existingList = getList<T, String>(obj, col)
        if (list !is ManagedRealmList || !RealmInterop.realm_equals(existingList.nativePointer, list.nativePointer)) {
            existingList.also {
                it.clear()
                it.addAll(list)
            }
        }
    }
}

// public inline fun <reified T : Any, S : T?> List<S>.kclass(): KClass<T> = T::class
