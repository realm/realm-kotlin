/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use th
 * is file except in compliance with the License.
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

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.interop.RealmInterop
import io.realm.isManaged
import io.realm.isValid
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

/**
 * Add a check and error message for code that never be reached because it should have been
 * replaced by the Compiler Plugin.
 */
@Suppress("FunctionNaming")
internal inline fun REPLACED_BY_IR(
    message: String = "This code should have been replaced by the Realm Compiler Plugin. " +
        "Has the `realm-kotlin` Gradle plugin been applied to the project?"
): Nothing = throw AssertionError(message)

internal fun checkRealmClosed(realm: RealmReference) {
    if (RealmInterop.realm_is_closed(realm.dbPointer)) {
        throw IllegalStateException("Realm has been closed and is no longer accessible: ${realm.owner.configuration.path}")
    }
}

@Suppress("TooGenericExceptionCaught") // Remove when errors are properly typed in https://github.com/realm/realm-kotlin/issues/70
internal fun <T : RealmObject> create(mediator: Mediator, realm: RealmReference, type: KClass<T>): T {
    // FIXME Does not work with obfuscation. We should probably supply the static meta data through
    //  the companion (accessible through schema) or might even have a cached version of the key in
    //  some runtime container of an open realm.
    //  https://github.com/realm/realm-kotlin/issues/85
    //  https://github.com/realm/realm-kotlin/issues/105
    val objectType = type.simpleName ?: error("Cannot get class name")
    try {
        val managedModel = mediator.createInstanceOf(type)
        val key = RealmInterop.realm_find_class(realm.dbPointer, objectType)
        return managedModel.manage(
            realm,
            mediator,
            type,
            RealmInterop.realm_object_create(realm.dbPointer, key)
        )
    } catch (e: RuntimeException) {
        // FIXME Throw proper exception
        //  https://github.com/realm/realm-kotlin/issues/70
        @Suppress("TooGenericExceptionThrown")
        throw RuntimeException("Failed to create object of type '$objectType'", e)
    }
}

@Suppress("TooGenericExceptionCaught") // Remove when errors are properly typed in https://github.com/realm/realm-kotlin/issues/70
internal fun <T : RealmObject> create(
    mediator: Mediator,
    realm: RealmReference,
    type: KClass<T>,
    primaryKey: Any?
): T {
    // FIXME Does not work with obfuscation. We should probably supply the static meta data through
    //  the companion (accessible through schema) or might even have a cached version of the key in
    //  some runtime container of an open realm.
    //  https://github.com/realm/realm-kotlin/issues/85
    //  https://github.com/realm/realm-kotlin/issues/105
    val objectType = type.simpleName ?: error("Cannot get class name")
    try {
        val key = RealmInterop.realm_find_class(realm.dbPointer, objectType)
        // TODO Manually checking if object with same primary key exists. Should be thrown by C-API
        //  instead
        //  https://github.com/realm/realm-core/issues/4595
        val existingPrimaryKeyObject =
            RealmInterop.realm_object_find_with_primary_key(realm.dbPointer, key, primaryKey)
        existingPrimaryKeyObject?.let {
            // FIXME Throw proper exception
            //  https://github.com/realm/realm-kotlin/issues/70
            @Suppress("TooGenericExceptionThrown")
            throw RuntimeException("Cannot create object with existing primary key")
        }
        val managedModel = mediator.createInstanceOf(type)
        return managedModel.manage(
            realm,
            mediator,
            type,
            RealmInterop.realm_object_create_with_primary_key(realm.dbPointer, key, primaryKey)
        )
    } catch (e: RuntimeException) {
        // FIXME Throw proper exception
        //  https://github.com/realm/realm-kotlin/issues/70
        @Suppress("TooGenericExceptionThrown")
        throw RuntimeException("Failed to create object of type '$objectType'", e)
    }
}

@Suppress("NestedBlockDepth")
internal fun <T> copyToRealm(
    mediator: Mediator,
    realmPointer: RealmReference,
    element: T,
    cache: MutableMap<RealmObjectInternal, RealmObjectInternal> = mutableMapOf()
): T {
    return if (element is RealmObjectInternal) {
        var elementToCopy = element

        // Throw if object is not valid
        if (!elementToCopy.isValid()) {
            throw IllegalStateException("Cannot copy an invalid managed object to Realm.")
        }

        // Copy object if it is not managed
        if (!elementToCopy.isManaged()) {
            val instance: RealmObjectInternal = element
            val companion = mediator.companionOf(instance::class)
            val members =
                companion.`$realm$fields` as List<KMutableProperty1<RealmObjectInternal, Any?>>

            val target = companion.`$realm$primaryKey`?.let { primaryKey ->
                create(
                    mediator,
                    realmPointer,
                    instance::class,
                    (primaryKey as KProperty1<RealmObjectInternal, Any?>).get(instance)
                )
            } ?: create(mediator, realmPointer, instance::class)

            cache[instance] = target

            // TODO OPTIMIZE We could set all properties at once with on C-API call
            for (member: KMutableProperty1<RealmObjectInternal, Any?> in members) {
                val targetValue = member.get(instance).let { sourceObject ->
                    // Check whether the source is a RealmObject, a primitive or a list
                    // In case of list ensure the values from the source are passed to the native list
                    if (sourceObject is RealmObjectInternal && !sourceObject.`$realm$IsManaged`) {
                        cache.getOrPut(sourceObject) {
                            copyToRealm(mediator, realmPointer, sourceObject, cache)
                        }
                    } else if (sourceObject is RealmList<*>) {
                        processListMember(
                            mediator,
                            realmPointer,
                            cache,
                            member,
                            target,
                            sourceObject
                        )
                    } else {
                        sourceObject
                    }
                }
                targetValue?.let {
                    // TODO OPTIMIZE Should we do a separate setter that allows the isDefault flag for sync
                    //  optimizations
                    member.set(target, it)
                }
            }
            elementToCopy = target as T
        }

        elementToCopy
    } else {
        // Ignore copy if the element is of a primitive type
        element
    }
}

@Suppress("LongParameterList")
private fun <T : RealmObject> processListMember(
    mediator: Mediator,
    realmPointer: RealmReference,
    cache: MutableMap<RealmObjectInternal, RealmObjectInternal>,
    member: KMutableProperty1<T, Any?>,
    target: T,
    sourceObject: RealmList<*>
): RealmList<Any?> {
    @Suppress("UNCHECKED_CAST")
    val list = member.get(target) as RealmList<Any?>
    for (item in sourceObject) {
        // Same as in copyToRealm, check whether we are working with a primitive or a RealmObject
        if (item is RealmObjectInternal && !item.`$realm$IsManaged`) {
            val value = cache.getOrPut(item) {
                copyToRealm(mediator, realmPointer, item, cache)
            }
            list.add(value)
        } else {
            list.add(item)
        }
    }
    return list
}
