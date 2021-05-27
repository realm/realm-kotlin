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

import io.realm.RealmObject
import io.realm.interop.Link
import io.realm.interop.RealmInterop

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
    fun <R> getValue(obj: RealmObjectInternal, col: String): Any? {
        val realm = obj.`$realm$Owner` as TransactionId? ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        return RealmInterop.realm_get_value(o, key)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    inline fun <reified R : RealmObject> getObject(
        obj: RealmObjectInternal,
        col: String,
    ): Any? {
        val realm = obj.`$realm$Owner` as TransactionId? ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        val link = RealmInterop.realm_get_value<Link>(o, key)
        if (link != null) {
            val value =
                (obj.`$realm$Mediator` as Mediator).createInstanceOf(R::class)
            return value.link(
                obj.`$realm$Owner` as TransactionId,
                obj.`$realm$Mediator` as Mediator,
                R::class,
                link
            )
        }
        return null
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    fun <R> setValue(obj: RealmObjectInternal, col: String, value: R) {
        val realm = obj.`$realm$Owner` as TransactionId? ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm.dbPointer, obj.`$realm$TableName`!!, col)
        // TODO Consider making a RealmValue cinterop type and move the various to_realm_value
        //  implementations in the various platform RealmInterops here to eliminate
        //  RealmObjectInterop and make cinterop operate on primitive values and native pointers
        //  only. This relates to the overall concern of having a generic path for getter/setter
        //  instead of generating a typed path for each type.
        RealmInterop.realm_set_value(o, key, value, false)
    }

    @Suppress("unused") // Called from generated code
    inline fun <reified R : RealmObjectInternal> setObject(
        obj: RealmObjectInternal,
        col: String,
        value: R?
    ) {
        val newValue = if (value?.`$realm$IsManaged` == false) {
            copyToRealm(obj.`$realm$Mediator` as Mediator, obj.`$realm$Owner` as TransactionId, value)
        } else value
        setValue(obj, col, newValue)
    }
}
