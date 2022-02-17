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
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import kotlin.reflect.KClass

// TODO API-INTERNAL
// We could inline this
internal fun <T : RealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    mediator: Mediator,
    className: String,
    objectPointer: NativePointer
): T {
    this.`$realm$IsManaged` = true
    this.`$realm$Owner` = realm
    this.`$realm$ClassName` = className
    this.`$realm$ObjectPointer` = objectPointer
    this.`$realm$metadata` = realm.schemaMetadata[className]
    // FIXME API-LIFECYCLE Initialize actual link; requires handling of link in compiler plugin
    // this.link = RealmInterop.realm_object_as_link()
    this.`$realm$Mediator` = mediator
    @Suppress("UNCHECKED_CAST")
    return this as T
}

// TODO API-INTERNAL
internal fun <T : RealmObject> RealmObjectInternal.link(
    realm: RealmReference,
    mediator: Mediator,
    className: String?,
    link: Link
): T {
    this.`$realm$ObjectPointer` = RealmInterop.realm_get_object(realm.dbPointer, link)
    this.`$realm$IsManaged` = true
    this.`$realm$Owner` = realm
    this.`$realm$ClassName` = className ?: RealmInterop.realm_get_class(realm.dbPointer, RealmInterop.realm_object_get_table(this.`$realm$ObjectPointer`!!)).name
    // FIXME API-LIFECYCLE Could be lazy loaded from link; requires handling of link in compiler plugin
    this.`$realm$metadata` = realm.schemaMetadata[this.`$realm$ClassName`!!]
    this.`$realm$Mediator` = mediator
    @Suppress("UNCHECKED_CAST")
    return this as T
}

/**
 * Creates a frozen copy of this object.
 *
 * @param frozenRealm Pointer to frozen Realm to which the frozen copy should belong.
 */
internal fun <T : RealmObject> RealmObjectInternal.freeze(frozenRealm: RealmReference): T {
    @Suppress("UNCHECKED_CAST")
    return this.freeze(frozenRealm) as T
}

/**
 * Creates a live copy of this object.
 *
 * @param liveRealm Reference to the Live Realm that should own the thawed object.
 */
internal fun <T : RealmObject> RealmObjectInternal.thaw(liveRealm: LiveRealmReference): T? {
    @Suppress("UNCHECKED_CAST")
    return this.thaw(liveRealm)?.let { it as T }
}

/**
 * Instantiates a [RealmObject] from its Core [Link] representation. For internal use only.
 */
internal fun <T : RealmObject> Link.toRealmObject(
    className: String?,
    clazz: KClass<T>,
    mediator: Mediator,
    realm: RealmReference
): T {
    return mediator.createInstanceOf(clazz)
        .link(realm, mediator, className, this)
}

/**
 * Returns the [RealmObjectCompanion] associated with a given [RealmObject]'s [KClass].
 */
internal inline fun <reified T : RealmObject> KClass<T>.realmObjectCompanion(): RealmObjectCompanion {
    return io.realm.internal.platform.realmObjectCompanion(this)
}
