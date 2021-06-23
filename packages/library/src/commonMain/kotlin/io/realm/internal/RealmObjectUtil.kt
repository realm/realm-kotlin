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

import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

// TODO API-INTERNAL
// We could inline this
fun <T : RealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    objectPointer: NativePointer
): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Owner` = realm
    this.`$realm$TableName` = type.simpleName
    this.`$realm$ObjectPointer` = objectPointer
    // FIXME API-LIFECYCLE Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    this.`$realm$Mediator` = mediator
    @Suppress("UNCHECKED_CAST")
    return this as T
}

// TODO API-INTERNAL
fun <T : RealmObject> RealmObjectInternal.link(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    link: Link
): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Owner` = realm
    this.`$realm$TableName` = type.simpleName
    // FIXME API-LIFECYCLE Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm.dbPointer, link)
    this.`$realm$Mediator` = mediator
    @Suppress("UNCHECKED_CAST")
    return this as T
}

fun RealmObjectInternal.unmanage() {
    // FIXME API-LIFECYCLE For now update the object to an inconsistent state that triggers Realm setters and
    //  getters to raise an IllegalStateException by keeping the `$realm$IsManaged` property set to
    //  true (triggers delegation to Realm-backed getter/setter) while clearing the native
    //  pointers (triggers the native getter/setter to throw the IllegalStateException).
    this.`$realm$IsManaged` = true
    this.`$realm$ObjectPointer` = null
    this.`$realm$Owner` = null
}

/**
 * Creates a frozen copy of this object.
 *
 * @param frozenRealm Pointer to frozen Realm to which the frozen copy should belong.
 */
fun <T : RealmObject> RealmObjectInternal.freeze(frozenRealm: RealmReference): T {
    @Suppress("UNCHECKED_CAST")
    val type: KClass<T> = this::class as KClass<T>
    val managedModel = (`$realm$Mediator` as Mediator).createInstanceOf(type)
    return managedModel.manage(
        frozenRealm!!,
        `$realm$Mediator` as Mediator,
        type,
        RealmInterop.realm_object_freeze(
            `$realm$ObjectPointer`!!,
            frozenRealm.dbPointer
        )
    )
}

/**
 * Creates a live copy of this object.
 *
 * @param liveRealm Reference to the Live Realm that should own the thawed object.
 */
internal fun <T : RealmObject> RealmObjectInternal.thaw(liveRealm: BaseRealm): T? {
    @Suppress("UNCHECKED_CAST")
    val type: KClass<T> = this::class as KClass<T>
    val managedModel = (`$realm$Mediator` as Mediator).createInstanceOf(type)
    val dbPointer = liveRealm.realmReference.dbPointer
    // FIXME C-API is currently throwing an error if the object has been deleted, so currently just
    //  catching that and returning null
    @Suppress("TooGenericExceptionCaught")
    try {
        return RealmInterop.realm_object_thaw(`$realm$ObjectPointer`!!, dbPointer)!!.let { thawedObject ->
            managedModel.manage(
                liveRealm.realmReference,
                `$realm$Mediator` as Mediator,
                type,
                thawedObject
            )
        }
    } catch (e: Exception) {
        return null
    }
}
