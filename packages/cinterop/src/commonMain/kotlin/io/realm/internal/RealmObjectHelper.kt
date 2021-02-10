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
}
