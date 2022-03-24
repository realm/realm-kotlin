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
    this.`$realm$objectReference` = RealmObjectReference(
        type = type,
        owner = realm,
        mediator = mediator,
        objectPointer = objectPointer,
        className = if (this@manage is DynamicRealmObject) {
            RealmInterop.realm_get_class(
                realm.dbPointer,
                RealmInterop.realm_object_get_table(objectPointer)
            ).name
        } else {
            realmObjectCompanionOrThrow(type).`$realm$className`
        }
    )

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
@Suppress("UNCHECKED_CAST")
internal fun <T : RealmObject> RealmObject.freeze(frozenRealm: RealmReference): T =
    getObjectReference()?.freeze(frozenRealm)?.toRealmObject() as T

/**
 * Creates a live copy of this object.
 *
 * @param liveRealm Reference to the Live Realm that should own the thawed object.
 */
@Suppress("UNCHECKED_CAST")
internal fun <T : RealmObject> RealmObject.thaw(liveRealm: LiveRealmReference): T? =
    getObjectReference()?.thaw(liveRealm)?.toRealmObject() as T?

/**
 * Instantiates a [RealmObject] from its Core [Link] representation. For internal use only.
 */
internal fun <T : RealmObject> Link.toRealmObject(
    clazz: KClass<T>,
    mediator: Mediator,
    realm: RealmReference
): T = mediator.createInstanceOf(clazz).link(
    realm = realm,
    mediator = mediator,
    type = clazz,
    link = this
)

/**
 * Instantiates a [RealmObject] from its Core [NativePointer] representation. For internal use only.
 */
internal fun <T : RealmObject> NativePointer.toRealmObject(
    clazz: KClass<T>,
    mediator: Mediator,
    realm: RealmReference
): T = mediator.createInstanceOf(clazz).manage(
    realm = realm,
    mediator = mediator,
    type = clazz,
    objectPointer = this
)

/**
 * Instantiates a [RealmObject] from its Core [RealmObjectReference] representation. For internal use only.
 */
internal fun <T : RealmObject> RealmObjectReference<T>.toRealmObject(): T =
    mediator.createInstanceOf(type)
        .manage(
            realm = owner,
            mediator = mediator,
            type = type,
            objectPointer = objectPointer,
        )

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

/**
 * If the Realm object is managed returns its Realm Object reference, otherwise returns null.
 */
internal fun RealmObject.getObjectReference(): RealmObjectReference<out RealmObject>? {
    return (this as RealmObjectInternal).`$realm$objectReference`
}

/**
 * If the Realm Object is managed it calls the specified function block and returns its result,
 * otherwise returns null.
 */
internal inline fun <R> RealmObject.runIfManaged(block: RealmObjectReference<out RealmObject>.() -> R?): R? =
    getObjectReference()?.run(block)

/**
 * Checks whether [this] and [other] represent the same underlying object or not. It allows to check
 * if two object from different frozen realms share their object key, and thus represent the same
 * object at different points in time (= at two different frozen realm versions).
 */
internal fun RealmObject.hasSameObjectKey(other: RealmObject?): Boolean {
    if (other == null) return false

    return runIfManaged {
        val that = this
        other.runIfManaged {
            val thisKey =
                RealmInterop.realm_object_get_key(this.objectPointer)
            val otherKey =
                RealmInterop.realm_object_get_key(that.objectPointer)

            thisKey == otherKey
        }
    } ?: throw IllegalStateException("Cannot compare unmanaged objects.")
}
