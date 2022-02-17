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

import io.realm.DynamicRealmObject
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
import io.realm.internal.schema.RealmStorageTypeImpl
import io.realm.internal.util.Validation.sdkError
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

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> getValue(obj: RealmObjectInternal, propertyName: String): Any? {
        obj.checkValid()
        return getByKey<R>(obj, obj.propertyKeyOrThrow(propertyName))
    }

    internal fun <R> getByKey(obj: RealmObjectInternal, key: io.realm.internal.interop.PropertyKey): Any? {
        // TODO Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
        val o = obj.`$realm$ObjectPointer`!! ?: sdkError("Cannot retrieve property value in a realm for an unmanaged objects")
        return RealmInterop.realm_get_value(o, key)
    }

    @Suppress("unused") // Called from generated code
    // FIXME Why do we have a generic parameter on this?
    internal fun <R> getTimestamp(obj: RealmObjectInternal, col: String): RealmInstant? {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$ClassName`!!, col)
        val res = RealmInterop.realm_get_value<Timestamp?>(o, key)
        return if (res == null) null else RealmInstantImpl(res)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject> getObject(
        obj: RealmObjectInternal,
        propertyName: String,
    ): Any? {
        obj.checkValid()
        return getObjectByKey<R>(obj, obj.propertyKeyOrThrow(propertyName))
    }

    internal inline fun <reified R : RealmObject> getObjectByKey(
        obj: RealmObjectInternal,
        key: io.realm.internal.interop.PropertyKey,
    ): Any? {
        // TODO Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val link = RealmInterop.realm_get_value<Link?>(o, key)
        if (link != null) {
            val value = (obj.`$realm$Mediator`!!).createInstanceOf(R::class)
            return value.link(
                obj.`$realm$Owner`!!,
                obj.`$realm$Mediator`!!,
                obj.`$realm$ClassName`!!,
                link
            )
        }
        return null
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    internal inline fun <reified R : Any> getList(
        obj: RealmObjectInternal,
        propertyName: String
    ): RealmList<Any?> {
        return getList(obj, propertyName, R::class)
    }

    internal fun <R : Any> getList(
        obj: RealmObjectInternal,
        propertyName: String,
        elementType: KClass<R>,
    ): RealmList<Any?> {
        return getListByKey(obj, obj.propertyKeyOrThrow(propertyName), elementType)
    }

    internal fun <R : Any> getListByKey(
        obj: RealmObjectInternal,
        key: io.realm.internal.interop.PropertyKey,
        elementType: KClass<R>,
    ): RealmList<Any?> {
        // TODO Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val listPtr: NativePointer = RealmInterop.realm_get_list(o, key)
        val mediator: Mediator = obj.`$realm$Mediator`!!

        // FIXME Error could be eliminated if we only reached here on a ManagedRealmObject (or something like that)
        val realm: RealmReference =
            obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        // Cannot call managedRealmList directly from an inline function
        return getManagedRealmList(listPtr, obj.`$realm$ClassName`!!, elementType, mediator, realm)
    }

    /**
     * Helper function that returns a managed list. This is needed due to the restriction of inline
     * functions not being able to access non-public API methods - managedRealmList is `internal`
     * and therefore it cannot be called from `getList`
     */
    internal fun getManagedRealmList(
        listPtr: NativePointer,
        className: String,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference
    ): RealmList<Any?> {
        return managedRealmList(
            listPtr,
            ListOperatorMetadata(
                className,
                clazz = clazz,
                mediator = mediator,
                realm = realm
            )
        )
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> setValue(obj: RealmObjectInternal, propertyName: String, value: R) {
        obj.checkValid()
        // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
        //  implementations in the various platform RealmInterops here to eliminate
        //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        try {
            setValueByKey<R>(obj, obj.propertyKeyOrThrow(propertyName), value)
        }
        // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
        // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
        // info that might help users to understand the exception.
        catch (exception: RealmCorePropertyNotNullableException) {
            throw IllegalArgumentException("Required property `${obj.`$realm$ClassName`}.$propertyName` cannot be null")
        } catch (exception: RealmCorePropertyTypeMismatchException) {
            throw IllegalArgumentException("Property `${obj.`$realm$ClassName`}.$propertyName` cannot be assigned with value '$value' of wrong type")
        } catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.`$realm$ClassName`}.$propertyName` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> setTimestamp(obj: RealmObjectInternal, col: String, value: RealmInstant?) {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$ClassName`!!, col)
        // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
        //  implementations in the various platform RealmInterops here to eliminate
        //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        try {
            RealmInterop.realm_set_value(o, key, value, false)
        }
        // The catch block should catch specific Core exceptions and rethrow them as Kotlin exceptions.
        // Core exceptions meaning might differ depending on the context, by rethrowing we can add some context related
        // info that might help users to understand the exception.
        catch (exception: RealmCoreException) {
            throw IllegalStateException(
                "Cannot set `${obj.`$realm$ClassName`}.$col` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    @Suppress("unused") // Called from generated code
    internal fun <R> setValueByKey(
        obj: RealmObjectInternal,
        key: io.realm.internal.interop.PropertyKey,
        value: R
    ) {
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        RealmInterop.realm_set_value(o, key, value, false)
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObjectInternal> setObject(
        obj: RealmObjectInternal,
        propertyName: String,
        value: R?
    ) {
        obj.checkValid()
        val newValue = if (value?.`$realm$IsManaged` == false) {
            copyToRealm(obj.`$realm$Mediator`!!, obj.`$realm$Owner`!!, value)
        } else value
        setValueByKey(obj, obj.propertyKeyOrThrow(propertyName), newValue)
    }

    internal fun <R : Any> dynamicGet(obj: RealmObjectInternal, clazz: KClass<R>, propertyName: String): R? {
        @Suppress("UNCHECKED_CAST")
        return when (clazz) {
            RealmInstant::class -> getTimestamp<R>(obj, propertyName)
            DynamicRealmObject::class -> getObject<DynamicRealmObject>(obj, propertyName)
            DynamicMutableRealmObject::class -> getObject<DynamicMutableRealmObject>(obj, propertyName)
            RealmList::class -> throw IllegalArgumentException("Cannot retrieve RealmList through 'get(...)', use getList(...) instead: $propertyName")
            else -> getValue<R>(obj, propertyName)
        } as R?
    }

    @Suppress("UNUSED_PARAMETER")
    internal fun setList(obj: RealmObjectInternal, col: String, list: RealmList<Any?>) {
        TODO()
    }
}
