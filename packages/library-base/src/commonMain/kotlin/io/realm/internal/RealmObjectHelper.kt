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
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
import kotlin.reflect.KClass

object RealmObjectHelper {
    // Issues (not yet fully uncovered/filed) met when calling these or similar methods from
    // generated code
    // - Generic return type should be R but causes compilation errors for native
    //  e: java.lang.IllegalStateException: Not found Idx for public io.realm.internal/RealmObjectHelper|null[0]/
    // - Passing KProperty1<T,R> with inlined reified type parameters to enable fetching type and
    //   property names directly from T/property triggers runtime crash for primitive properties on
    //   Kotlin native. Seems to be an issue with boxing/unboxing

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> getValue(obj: RealmObjectInternal, col: String): Any? {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        return RealmInterop.realm_get_value(o, key)
    }

    @Suppress("unused") // Called from generated code
    internal fun <R> getTimestamp(obj: RealmObjectInternal, col: String): RealmInstant? {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        val res = RealmInterop.realm_get_value<Timestamp>(o, key)
        return if (res == null) null else RealmInstantImpl(res)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObject> getObject(
        obj: RealmObjectInternal,
        col: String,
    ): Any? {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        val link = RealmInterop.realm_get_value<Link>(o, key)
        if (link != null) {
            val value =
                (obj.`$realm$Mediator`!!).createInstanceOf(R::class)
            return value.link(
                obj.`$realm$Owner`!!,
                obj.`$realm$Mediator`!!,
                R::class,
                link
            )
        }
        return null
    }

    // Return type should be RealmList<R?> but causes compilation errors for native
    internal inline fun <reified R> getList(
        obj: RealmObjectInternal,
        col: String
    ): RealmList<Any?> {
        val realm: RealmReference =
            obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key: PropertyKey =
            RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        val listPtr: NativePointer = RealmInterop.realm_get_list(o, key)
        val clazz: KClass<*> = R::class
        val mediator: Mediator = obj.`$realm$Mediator`!!

        // Cannot call managedRealmList directly from an inline function
        return getManagedRealmList(listPtr, clazz, mediator, realm)
    }

    /**
     * Helper function that returns a managed list. This is needed due to the restriction of inline
     * functions not being able to access non-public API methods - managedRealmList is `internal`
     * and therefore it cannot be called from `getList`
     */
    internal fun getManagedRealmList(
        listPtr: NativePointer,
        clazz: KClass<*>,
        mediator: Mediator,
        realm: RealmReference
    ): RealmList<Any?> {
        return managedRealmList(
            listPtr,
            ListOperatorMetadata(
                clazz = clazz,
                mediator = mediator,
                realm = realm
            )
        )
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    internal fun <R> setValue(obj: RealmObjectInternal, col: String, value: R) {
        obj.checkValid()
        val realm = obj.`$realm$Owner` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
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
                "Cannot set `${obj.`$realm$TableName`}.$col` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
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
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
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
                "Cannot set `${obj.`$realm$TableName`}.$col` to `$value`: changing Realm data can only be done on a live object from inside a write transaction. Frozen objects can be turned into live using the 'MutableRealm.findLatest(obj)' API.",
                exception
            )
        }
    }

    @Suppress("unused") // Called from generated code
    internal inline fun <reified R : RealmObjectInternal> setObject(
        obj: RealmObjectInternal,
        col: String,
        value: R?
    ) {
        obj.checkValid()
        val newValue = if (value?.`$realm$IsManaged` == false) {
            copyToRealm(obj.`$realm$Mediator`!!, obj.`$realm$Owner`!!, value)
        } else value
        setValue(obj, col, newValue)
    }

    internal fun setList(obj: RealmObjectInternal, col: String, list: RealmList<Any?>) {
        TODO()
    }
}
