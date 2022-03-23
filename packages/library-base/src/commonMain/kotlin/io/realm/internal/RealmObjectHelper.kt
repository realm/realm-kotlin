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
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCorePropertyNotNullableException
import io.realm.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
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

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> getValue(obj: ObjectReference<out RealmObject>, propertyName: String): Any? {
        obj.checkValid()
        return getValueByKey<R>(obj, obj.propertyInfoOrThrow(propertyName).key)
    }

    internal fun <R> getValueByKey(
        obj: ObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey
    ): Any? {
        val o = obj.objectPointer
        return RealmInterop.realm_get_value(o, key)
    }

    @Suppress("unused") // Called from generated code
    internal fun <R> getTimestamp(obj: ObjectReference<out RealmObject>, propertyName: String): RealmInstant? {
        obj.checkValid()
        val o = obj.objectPointer
        val res = RealmInterop.realm_get_value<Timestamp?>(o, obj.propertyInfoOrThrow(propertyName).key)
        return if (res == null) null else RealmInstantImpl(res)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject> getObject(
        obj: ObjectReference<out RealmObject>,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        return getObjectByKey<R>(obj, obj.propertyInfoOrThrow(propertyName).key)
    }

    internal inline fun <reified R : RealmObject> getObjectByKey(
        obj: ObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
    ): Any? {
        val o = obj.objectPointer
        return RealmInterop.realm_get_value<Link?>(o, key)?.toRealmObject(
            clazz = R::class,
            mediator = obj.mediator,
            realm = obj.owner
        )
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    internal inline fun <reified R : Any> getList(
        obj: ObjectReference<out RealmObject>,
        propertyName: String
    ): ManagedRealmList<Any?> {
        return getList(obj, propertyName, R::class)
    }

    internal fun <R : Any> getList(
        obj: ObjectReference<out io.realm.RealmObject>,
        propertyName: String,
        elementType: KClass<R>,
    ): ManagedRealmList<Any?> {
        return getListByKey(obj, obj.propertyInfoOrThrow(propertyName).key, elementType)
    }

    internal fun <R : Any> getListByKey(
        obj: ObjectReference<out io.realm.RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
        elementType: KClass<R>,
    ): ManagedRealmList<Any?> {
        val o = obj.objectPointer
        val listPtr: NativePointer = RealmInterop.realm_get_list(o, key)
        val mediator: Mediator = obj.mediator

        val realm: RealmReference = obj.owner
        // Cannot call managedRealmList directly from an inline function
        return getManagedRealmList(listPtr, elementType, mediator, realm)
    }

    /**
     * Helper function that returns a managed list. This is needed due to the restriction of inline
     * functions not being able to access non-public API methods - managedRealmList is `internal`
     * and therefore it cannot be called from `getList`
     */
    internal fun <R> getManagedRealmList(
        listPtr: NativePointer,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference
    ): ManagedRealmList<R> {
        return managedRealmList(
            listPtr,
            ListOperatorMetadata(
                mediator = mediator,
                realm = realm,
                converter(mediator, realm, clazz),
            )
        )
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> setValue(obj: ObjectReference<out RealmObject>, propertyName: String, value: R) {
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
                val name = classMetaData.get(primaryKeyPropertyKey)!!.name
                throw IllegalArgumentException("Cannot update primary key property '${obj.className}.$name'")
            }
        }
        setValueByKey<R>(obj, key, value)
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> setTimestamp(
        obj: ObjectReference<out RealmObject>,
        propertyName: String,
        value: RealmInstant?
    ) {
        obj.checkValid()
        val realm = obj.owner
        val o = obj.objectPointer
        // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
        //  implementations in the various platform RealmInterops here to eliminate
        //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        try {
            RealmInterop.realm_set_value(o, obj.propertyInfoOrThrow(propertyName).key, value, false)
        }
        // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
        // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
        // info that might help users to understand the exception.
        catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.className}.$propertyName` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    @Suppress("unused") // Called from generated code
    internal fun <R> setValueByKey(
        obj: ObjectReference<out RealmObject>,
        key: io.realm.internal.interop.PropertyKey,
        value: R
    ) {
        val o = obj.objectPointer
        try {
            // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
            //  implementations in the various platform RealmInterops here to eliminate
            //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
            //  only. This relates to the overall concern of having a generic path for getter/setter
            //  instead of generating a typed path for each type.
            RealmInterop.realm_set_value(o, key, value, false)
            // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
            // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
            // info that might help users to understand the exception.
        } catch (exception: RealmCorePropertyNotNullableException) {
            throw IllegalArgumentException("Required property `${obj.className}.${obj.metadata.get(key)!!.name}` cannot be null")
        } catch (exception: RealmCorePropertyTypeMismatchException) {
            throw IllegalArgumentException("Property `${obj.className}.${obj.metadata.get(key)!!.name}` cannot be assigned with value '$value' of wrong type")
        } catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.className}.$${obj.metadata.get(key)!!.name}` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObjectInternal> setObject(
        obj: ObjectReference<out RealmObject>,
        propertyName: String,
        value: R?
    ) {
        obj.checkValid()
        val newValue = if (
            value != null &&
            (!value.isManaged() || obj.owner != (value as RealmObjectInternal).asObjectReference()!!.owner)
        ) {
            copyToRealm(obj.mediator, obj.owner, value)
        } else {
            value
        }
        setValueByKey(obj, obj.propertyInfoOrThrow(propertyName).key, newValue?.asObjectReference())
    }

    /**
     * Get values for non-collection properties by name.
     *
     * This will verify that the requested type (`clazz`) and nullability matches the property
     * properties in the schema.
     */
    internal fun <R : Any> dynamicGet(
        obj: ObjectReference<out RealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): R? {
        obj.checkValid()
        val propertyInfo = checkPropertyType(obj, propertyName, CollectionType.RLM_COLLECTION_TYPE_NONE, clazz, nullable)
        val value = when (clazz) {
            RealmInstant::class -> getTimestamp<R>(obj, propertyName)
            DynamicRealmObject::class -> getObjectByKey<DynamicRealmObject>(obj, propertyInfo.key)
            DynamicMutableRealmObject::class -> getObjectByKey<DynamicMutableRealmObject>(
                obj,
                propertyInfo.key
            )
            else -> getValueByKey<R>(obj, propertyInfo.key)
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
        obj: ObjectReference<out RealmObject>,
        propertyName: String,
        clazz: KClass<R>,
        nullable: Boolean
    ): RealmList<R?> {
        obj.checkValid()
        val propertyInfo = checkPropertyType(obj, propertyName, CollectionType.RLM_COLLECTION_TYPE_LIST, clazz, nullable)
        @Suppress("UNCHECKED_CAST")
        return getListByKey(obj, propertyInfo.key, clazz) as RealmList<R?>
    }

    private fun checkPropertyType(obj: ObjectReference<out RealmObject>, propertyName: String, collectionType: CollectionType, elementType: KClass<*>, nullable: Boolean): PropertyInfo {
        val realElementType = when (elementType) {
            DynamicRealmObject::class,
            DynamicMutableRealmObject::class ->
                RealmObject::class
            else -> elementType
        }
        val classMetadata = obj.metadata
        return classMetadata.getOrThrow(propertyName).also { propertyInfo ->
            val kClass = RealmStorageTypeImpl.fromCorePropertyType(propertyInfo.type).kClass
            if (collectionType != propertyInfo.collectionType ||
                realElementType != kClass ||
                nullable != propertyInfo.isNullable
            ) {
                throw IllegalArgumentException("Trying to access property '${obj.className}.$propertyName' as type: '${formatType(collectionType, realElementType, nullable)}' but actual schema type is '${formatType(propertyInfo.collectionType, kClass, propertyInfo.isNullable)}'")
            }
        }
    }

    internal fun <R> dynamicSetValue(obj: ObjectReference<out RealmObject>, propertyName: String, value: R) {
        obj.checkValid()
        setValueByKey<R>(obj, obj.propertyInfoOrThrow(propertyName).key, value)
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
    inline fun <reified T : Any> setList(obj: ObjectReference<out RealmObject>, col: String, list: RealmList<Any?>) {
        val existingList = getList<T>(obj, col)
        if (list !is ManagedRealmList || !RealmInterop.realm_equals(existingList.nativePointer, list.nativePointer)) {
            existingList.also {
                it.clear()
                it.addAll(list)
            }
        }
    }
}
