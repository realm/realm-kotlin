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

import io.realm.DynamicRealmObject
import io.realm.RealmObject
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.realmObjectCompanion
import io.realm.internal.util.Validation.sdkError
import kotlin.reflect.KClass

internal fun <T : RealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    objectPointer: NativePointer
): T {
    val className = type.simpleName ?: sdkError("Couldn't obtain class name for $type")
    this.`$realm$IsManaged` = true
    this.`$realm$Owner` = realm
    this.`$realm$Mediator` = mediator
    this.`$realm$ObjectPointer` = objectPointer
    this.`$realm$ClassName` = if (this is DynamicRealmObject) {
        RealmInterop.realm_get_class(`$realm$Owner`!!.dbPointer, RealmInterop.realm_object_get_table(objectPointer)).name
    } else {
        realmObjectCompanion(type).`$realm$className`
    }
    this.`$realm$metadata` = realm.schemaMetadata[this.`$realm$ClassName`!!]
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
internal inline fun <reified T : RealmObject> KClass<T>.realmObjectCompanion(): RealmObjectCompanion {
    return io.realm.internal.platform.realmObjectCompanion(this)
}
