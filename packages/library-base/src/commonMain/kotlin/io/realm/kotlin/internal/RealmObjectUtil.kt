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

package io.realm.kotlin.internal

import io.realm.kotlin.VersionId
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.Link
import io.realm.kotlin.internal.interop.ObjectKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmObjectPointer
import io.realm.kotlin.internal.platform.realmObjectCompanionOrNull
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass

internal fun <T : BaseRealmObject> RealmObjectInternal.manage(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    objectPointer: RealmObjectPointer
): T {
    this.io_realm_kotlin_objectReference = RealmObjectReference(
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
            realmObjectCompanionOrThrow(type).io_realm_kotlin_className
        }
    )

    @Suppress("UNCHECKED_CAST")
    return this as T
}

internal fun <T : BaseRealmObject> RealmObjectInternal.link(
    realm: RealmReference,
    mediator: Mediator,
    type: KClass<T>,
    link: Link
): T {
    val objectPointer = RealmInterop.realm_get_object(realm.dbPointer, link)
    return this.manage(realm, mediator, type, objectPointer)
}

/**
 * Instantiates a [BaseRealmObject] from its Core [Link] representation. For internal use only.
 */
internal fun <T : BaseRealmObject> Link.toRealmObject(
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
 * Instantiates a [BaseRealmObject] from its Core [NativePointer] representation. For internal use only.
 */
internal fun <T : BaseRealmObject> RealmObjectPointer.toRealmObject(
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
 * Instantiates a [BaseRealmObject] from its Core [RealmObjectReference] representation. For internal use only.
 */
internal fun <T : BaseRealmObject> RealmObjectReference<T>.toRealmObject(): T =
    mediator.createInstanceOf(type)
        .manage(
            realm = owner,
            mediator = mediator,
            type = type,
            objectPointer = objectPointer,
        )

/**
 * Returns the [RealmObjectCompanion] associated with a given [BaseRealmObject]'s [KClass].
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun KClass<*>.realmObjectCompanionOrNull(): RealmObjectCompanion? {
    @Suppress("invisible_reference", "invisible_member")
    return realmObjectCompanionOrNull(this)
}

/**
 * Returns the [RealmObjectCompanion] associated with a given [BaseRealmObject]'s [KClass].
 */
internal inline fun <reified T : BaseRealmObject> KClass<T>.realmObjectCompanionOrThrow(): RealmObjectCompanion {
    @Suppress("invisible_reference", "invisible_member")
    return realmObjectCompanionOrThrow(this)
}

/**
 * Convenience property to get easy access to the RealmObjectReference of a BaseRealmObject.
 *
 * This will be `null` for unmanaged objects.
 */
internal val <T : BaseRealmObject> T.realmObjectReference: RealmObjectReference<T>?
    @Suppress("UNCHECKED_CAST")
    get() = (this as RealmObjectInternal).io_realm_kotlin_objectReference as RealmObjectReference<T>?

/**
 * If the Realm Object is managed it calls the specified function block and returns its result,
 * otherwise returns null.
 */
internal inline fun <T : BaseRealmObject, R> T.runIfManaged(block: RealmObjectReference<T>.() -> R): R? =
    realmObjectReference?.run(block)

/**
 * Returns an identifier that uniquely identifies a RealmObject. This includes the version of the
 * object, so the same RealmObject at two different versions must have different identifiers,
 * even if all data inside the object is otherwise equal.
 */
internal fun BaseRealmObject.getIdentifier(): RealmObjectIdentifier {
    return runIfManaged {
        val classKey: ClassKey = metadata.classKey
        val objKey: ObjectKey = RealmInterop.realm_object_get_key(objectPointer)
        val version: VersionId = version()
        val path: String = owner.owner.configuration.path
        return RealmObjectIdentifier(classKey, objKey, version, path)
    } ?: throw IllegalStateException("Identifier can only be calculated for managed objects.")
}

public fun BaseRealmObject.getIdentifierOrNull(): RealmObjectIdentifier? {
    return runIfManaged {
        getIdentifier()
    }
}

/**
 * Checks whether [this] and [other] represent the same underlying object or not. It allows to check
 * if two object from different frozen realms share their object key, and thus represent the same
 * object at different points in time (= at two different frozen realm versions).
 */
internal fun BaseRealmObject.hasSameObjectKey(other: BaseRealmObject?): Boolean {
    if (other == null) return false

    return runIfManaged {
        val otherObjectPointer = this.objectPointer
        other.runIfManaged {
            val thisKey = RealmInterop.realm_object_get_key(this.objectPointer)
            val otherKey = RealmInterop.realm_object_get_key(otherObjectPointer)

            thisKey == otherKey
        }
    } ?: throw IllegalStateException("Cannot compare unmanaged objects.")
}

@Suppress("LongParameterList")
internal fun <T : BaseRealmObject> createDetachedCopy(
    mediator: Mediator,
    realmObject: T,
    currentDepth: UInt,
    maxDepth: UInt,
    closeAfterCopy: Boolean,
    cache: ManagedToUnmanagedObjectCache,
): T {
    val id = realmObject.getIdentifier()
    @Suppress("UNCHECKED_CAST")
    val result: BaseRealmObject = cache[id] as T? ?: run {
        val unmanagedObject = mediator.companionOf(realmObject::class).`io_realm_kotlin_newInstance`() as BaseRealmObject
        cache[id] = unmanagedObject
        RealmObjectHelper.assignValuesOnUnmanagedObject(
            unmanagedObject,
            realmObject,
            mediator,
            currentDepth,
            maxDepth,
            closeAfterCopy,
            cache
        )
        unmanagedObject
    }
    if (closeAfterCopy) {
        realmObject.realmObjectReference!!.objectPointer.release()
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}
