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
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.interop.RealmModelInternal
import kotlin.reflect.KClass

// TODO API-INTERNAL
// We could inline this
fun <T : RealmObject> RealmModelInternal.manage(realm: NativePointer, schema: Mediator, type: KClass<T>, objectPointer: NativePointer): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    this.`$realm$ObjectPointer` = objectPointer
    // FIXME API-LIFECYCLE Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    this.`$realm$Schema` = schema
    return this as T
}

// TODO API-INTERNAL
fun <T : RealmObject> RealmModelInternal.link(realm: NativePointer, schema: Mediator, type: KClass<T>, link: Link): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Pointer` = realm
    this.`$realm$TableName` = type.simpleName
    // FIXME API-LIFECYCLE Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm, link)
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
}
