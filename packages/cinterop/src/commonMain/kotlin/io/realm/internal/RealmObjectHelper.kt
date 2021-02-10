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

    // Return type should be R? but causes compilation errors for native
    inline fun <reified T, R> getValue(
        obj: RealmModelInternal,
        property: KProperty1<T, R>
    ): Any? {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, T::class.simpleName!!, property.name)
        return RealmInterop.realm_get_value(o, key)
    }

    // Return type should be R? but causes compilation errors for native
    inline fun <reified T, reified R : RealmModel> getObject(
        obj: RealmModelInternal,
        property: KProperty1<T, R>
    ): Any? {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, T::class.simpleName!!, property.name)
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

    inline fun <reified T, R> setValue(
        obj: RealmModelInternal,
        property: KProperty1<T, R>,
        value: R?
    ) {
        val realm = obj.`$realm$Pointer` ?: throw IllegalStateException("Invalid/deleted object")
        val o = obj.`$realm$ObjectPointer` ?: throw IllegalStateException("Invalid/deleted object")
        val key = RealmInterop.realm_get_col_key(realm, T::class.simpleName!!, property.name)
        RealmInterop.realm_set_value(o, key, value, false)
    }

    inline fun <reified T: RealmModel, reified R: RealmModelInternal> setObject(
        obj: RealmModelInternal,
        property1: KMutableProperty1<T, R>,
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
        setValue(obj, property1, newValue)
    }

    fun <T: RealmModel> copy(t1: T, t2: T): T {
        val members: List<KMutableProperty1<T, Any?>> = t1::class.members.filter { it is KMutableProperty1<*, *> } as List<KMutableProperty1<T, Any?>>
        for (member: KMutableProperty1<T, Any?> in members) {
            member.get(t1)?.let { member.set(t2, it) }
        }
        return t2
    }
}
