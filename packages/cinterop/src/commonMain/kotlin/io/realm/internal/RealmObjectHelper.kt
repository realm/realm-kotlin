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

import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

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
    fun <R> getValue(obj: RealmModelInternal, col: String): Any? {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, obj.`$realm$TableName`!!, col)
        return RealmInterop.realm_get_value(o, key)
    }

    // Return type should be R? but causes compilation errors for native
    @Suppress("unused") // Called from generated code
    inline fun <reified R : RealmModel> getObject(
        obj: RealmModelInternal,
        col: String,
    ): Any? {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, obj.`$realm$TableName`!!, col)
        val link = RealmInterop.realm_get_value<Link>(o, key)
        if (link != null) {
            val value =
                (obj.`$realm$Schema` as Mediator).newInstance(R::class) as RealmModelInternal
            return value.link(
                obj.`$realm$Pointer`!!,
                obj.`$realm$Schema` as Mediator,
                R::class,
                link
            )
        }
        return null
    }

    // Consider inlining
    @Suppress("unused") // Called from generated code
    fun <R> setValue(obj: RealmModelInternal, col: String, value: R) {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, obj.`$realm$TableName`!!, col)
        RealmInterop.realm_set_value(o, key, value, false)
    }

    @Suppress("unused")
    inline fun <reified R: RealmModelInternal> setObject(
        obj: RealmModelInternal,
        col: String,
        value: R?
    ) {
        val newValue = if (!(value?.`$realm$IsManaged` ?: true)) {
            val managedModel = (obj.`$realm$Schema` as Mediator).newInstance(R::class) as RealmModelInternal
            val realm = obj?.`$realm$Pointer`!!
            val key = RealmInterop.realm_find_class(realm, R::class.simpleName!!)
            val objectPointer = RealmInterop.realm_object_create(realm, key)
            managedModel.manage( realm, obj.`$realm$Schema` as Mediator, R::class, objectPointer)
            copy(value!!, managedModel)
        } else value
        setValue(obj, col, newValue)
    }

    fun <T: RealmModel> copy(t1: T, t2: T): T {
        val members: List<KMutableProperty1<T, Any?>> = t1::class.members.filter { it is KMutableProperty1<*, *> } as List<KMutableProperty1<T, Any?>>
        for (member: KMutableProperty1<T, Any?> in members) {
            member.get(t1)?.let { member.set(t2, it) }
        }
        return t2
    }
}
