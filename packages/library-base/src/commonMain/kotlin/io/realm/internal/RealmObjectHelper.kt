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

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.interop.CollectionType
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCorePropertyNotNullableException
import io.realm.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmListPointer
import io.realm.internal.interop.RealmValue
import io.realm.internal.schema.RealmStorageTypeImpl
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

    @Suppress("unused") // Called from generated code
    internal inline fun getValue(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
    ): RealmValue {
        obj.checkValid()
        return getValueByKey(obj, obj.propertyInfoOrThrow(propertyName).key)
    }

    internal inline fun getValueByKey(
        obj: RealmObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
    ): RealmValue = RealmInterop.realm_get_value(obj.objectPointer, key)

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject, U> getObject(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        return realmValueToRealmObject(getValue(obj, propertyName), R::class, obj.mediator, obj.owner)
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : Any> getList(
        obj: RealmObjectReference<out RealmObject>,
        propertyName: String
    ): ManagedRealmList<Any?> = getList<R>(obj, propertyName, R::class)

    internal fun <R> getList(
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
        val converter: RealmValueConverter<R> = converter<Any>(clazz, mediator, realm) as CompositeConverter<R, *>
        val metadata: ListOperatorMetadata<R> = ListOperatorMetadata(
            mediator = mediator,
            realm = realm,
            clazz,
            converter,
        )
        return managedRealmList(listPtr, metadata)
    }

    @Suppress("unused") // Called from generated code
    internal fun setValue(obj: RealmObjectReference<out RealmObject>, propertyName: String, value: RealmValue) {
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
        setValueByKey(obj, key, value)
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
            throw IllegalArgumentException("Property `${obj.className}.${obj.metadata[key]!!.name}` cannot be assigned with value '${value.value}' of wrong type")
        } catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.className}.$${obj.metadata[key]!!.name}` to `${value.value}`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
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
        val key = obj.propertyInfoOrThrow(propertyName).key
        val realmValue = realmObjectToRealmValue(value, obj.mediator, obj.owner)
        setValueByKey(obj, key, realmValue)
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified T : Any> setList(obj: RealmObjectReference<out RealmObject>, col: String, list: RealmList<Any?>) {
        val existingList = getList<T>(obj, col)
        if (list !is ManagedRealmList || !RealmInterop.realm_equals(existingList.nativePointer, list.nativePointer)) {
            existingList.also {
                it.clear()
                it.addAll(list)
            }
        }
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
        val propertyInfo =
            checkPropertyType(obj, propertyName, CollectionType.RLM_COLLECTION_TYPE_NONE, clazz, nullable)
        val realmValue = getValueByKey(obj, propertyInfo.key)
        // Consider moving this dynamic conversion to Converters.kt
        val value = when (clazz) {
            DynamicRealmObject::class,
            DynamicMutableRealmObject::class ->
                realmValueToRealmObject(realmValue, clazz as KClass<out RealmObject>, obj.mediator, obj.owner)
            else -> primitiveTypeConverters.getValue(clazz).realmValueToPublic(realmValue)
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
        return getListByKey<R>(obj, propertyInfo.key, clazz) as RealmList<R?>
    }

    internal fun <R> dynamicSetValue(obj: RealmObjectReference<out RealmObject>, propertyName: String, value: R) {
        obj.checkValid()
        val key = obj.propertyInfoOrThrow(propertyName).key
        val realmValue = when (value) {
            null -> RealmValue(null)
            is RealmObject -> realmObjectToRealmValue(value, obj.mediator, obj.owner)
            else -> {
                @Suppress("UNCHECKED_CAST")
                (primitiveTypeConverters.getValue(value!!::class) as RealmValueConverter<Any>).publicToRealmValue(
                    value
                )
            }
        }
        setValueByKey(obj, key, realmValue)
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

    private fun formatType(collectionType: CollectionType, elementType: KClass<*>, nullable: Boolean): String {
        val elementTypeString = elementType.toString() + if (nullable) "?" else ""
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> elementTypeString
            CollectionType.RLM_COLLECTION_TYPE_LIST -> "RealmList<$elementTypeString>"
            else -> TODO("Unsupported collection type: $collectionType")
        }
    }
}
