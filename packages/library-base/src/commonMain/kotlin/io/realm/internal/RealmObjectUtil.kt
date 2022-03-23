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
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.realmObjectCompanionOrNull
import io.realm.internal.platform.realmObjectCompanionOrThrow
import kotlin.reflect.KClass

internal fun <T : RealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    objectPointer: NativePointer
): T {
    this.`$realm$objectReference` = ObjectReference(type).apply {
        this.owner = realm
        this.mediator = mediator
        this.objectPointer = objectPointer
        this.className = if (this@manage is DynamicRealmObject) {
            RealmInterop.realm_get_class(owner.dbPointer, RealmInterop.realm_object_get_table(objectPointer)).name
        } else {
            realmObjectCompanionOrThrow(type).`$realm$className`
        }
        this.metadata = realm.schemaMetadata[this.className]!!
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
}

internal fun <T : RealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    type: KClass<T>,
    managedRealmObject: ObjectReference<T>
): T {
    this.`$realm$objectReference` = managedRealmObject.apply {
        this.className = if (this@manage is DynamicRealmObject) {
            RealmInterop.realm_get_class(owner.dbPointer, RealmInterop.realm_object_get_table(objectPointer)).name
        } else {
            realmObjectCompanionOrThrow(type).`$realm$className`
        }
        this.metadata = realm.schemaMetadata[this.className]!!
    }

    @Suppress("UNCHECKED_CAST")
    return this as T
}

internal fun <T : RealmObject> RealmObjectInternal.link(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    link: Link
): T {
    return this.manage(realm, mediator, type, RealmInterop.realm_get_object(realm.dbPointer, link))
}

/**
 * Creates a frozen copy of this object.
 *
 * @param frozenRealm Pointer to frozen Realm to which the frozen copy should belong.
 */
internal fun <T : RealmObject> RealmObjectInternal.freeze(frozenRealm: RealmReference): T =
    asObjectReference()?.freeze(frozenRealm)?.asRealmObject()!!

/**
 * Creates a live copy of this object.
 *
 * @param liveRealm Reference to the Live Realm that should own the thawed object.
 */
internal fun <T : RealmObject> RealmObjectInternal.thaw(liveRealm: LiveRealmReference): T? =
    asObjectReference()?.thaw(liveRealm)?.asRealmObject()

/**
 * Instantiates a [RealmObject] from its Core [Link] representation. For internal use only.
 */
internal fun <T : RealmObject> Link.toRealmObject(
    clazz: KClass<T>,
    mediator: Mediator,
    realm: RealmReference
): T {
    return mediator.createInstanceOf(clazz)
        .link(realm, mediator, clazz, this)
}

/**
 * Returns the [RealmObjectCompanion] associated with a given [RealmObject]'s [KClass].
 */
internal inline fun <reified T : Any> KClass<T>.realmObjectCompanionOrNull(): RealmObjectCompanion? {
    return realmObjectCompanionOrNull(this)
}

/**
 * Returns the [RealmObjectCompanion] associated with a given [RealmObject]'s [KClass].
 */
internal inline fun <reified T : RealmObject> KClass<T>.realmObjectCompanionOrThrow(): RealmObjectCompanion {
    return realmObjectCompanionOrThrow(this)
}
