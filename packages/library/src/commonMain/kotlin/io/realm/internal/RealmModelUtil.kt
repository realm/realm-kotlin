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

import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.worker.LiveRealm
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

// TODO API-INTERNAL
// We could inline this
fun <T : RealmObject<T>> RealmModelInternal.manage(realm: LiveRealm, schema: Mediator, type: KClass<T>, objectPointer: NativePointer): T {
    this.`$realm$IsManaged` = true
    this.`$realm$owner` = realm
    this.`$realm$Pointer` = realm.dbPointer
    this.`$realm$TableName` = type.simpleName
    this.`$realm$ObjectPointer` = objectPointer
    // FIXME API-LIFECYCLE Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    this.`$realm$Schema` = schema
    return this as T
}

// TODO API-INTERNAL
fun <T : RealmObject<T>> RealmModelInternal.link(realm: LiveRealm, schema: Mediator, type: KClass<T>, link: Link): T {
    this.`$realm$IsManaged` = true
    this.`$realm$owner` = realm
    this.`$realm$Pointer` = realm.dbPointer
    this.`$realm$TableName` = type.simpleName
    // FIXME API-LIFECYCLE Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm.dbPointer!!, link)
    this.`$realm$Schema` = schema
    return this as T
}

fun RealmModelInternal.unmanage() {
    // FIXME API-LIFECYCLE For now update the object to an inconsistent state that triggers Realm setters and
    //  getters to raise an IllegalStateException by keeping the `$realm$IsManaged` property set to
    //  true (triggers delegation to Realm-backed getter/setter) while clearing the native
    //  pointers (triggers the native getter/setter to throw the IllegalStateException).
    this.`$realm$IsManaged` = true
    this.`$realm$ObjectPointer` = null
    this.`$realm$Pointer` = null
    this.`$realm$owner` = null
}

// Create a frozen copy of this object
fun <T : RealmObject<T>> RealmModelInternal.freeze(realm: Realm): T {
    val type: KClass<T> = this::class as KClass<T>
    val managedModel = (`$realm$Schema` as Mediator).newInstance(type)
    return managedModel.manage(
        realm,
        `$realm$Schema` as Mediator,
        type,
        RealmInterop.realm_object_freeze(`$realm$ObjectPointer`!!, realm.dbPointer!!)
    )
}

// Create a live copy of a frozen object
fun <T : RealmObject<T>> RealmModelInternal.thaw(realm: LiveRealm): T {
    val type: KClass<T> = this::class as KClass<T>
    val managedModel = (`$realm$Schema` as Mediator).newInstance(type)
    return managedModel.manage(
        realm,
        `$realm$Schema` as Mediator,
        type,
        RealmInterop.realm_object_thaw(`$realm$ObjectPointer`!!, realm.dbPointer!!)
    )
}



