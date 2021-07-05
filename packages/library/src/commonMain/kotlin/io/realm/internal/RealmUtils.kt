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

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.interop.RealmInterop
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
fun <T : RealmObject> create(mediator: Mediator, realm: RealmReference, type: KClass<T>): T {
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
fun <T : RealmObject> create(
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
        val managedModel = mediator.createInstanceOf(type)
        val key = RealmInterop.realm_find_class(realm.dbPointer, objectType)
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

fun <T : RealmObject> copyToRealm(
    mediator: Mediator,
    realm: RealmReference,
    instance: T,
    cache: MutableMap<RealmObjectInternal, RealmObjectInternal> = mutableMapOf()
): T {
    // Copying already managed instance is an no-op
    if ((instance as RealmObjectInternal).`$realm$IsManaged`) return instance

    val companion = mediator.companionOf(instance::class)
    val members = companion.`$realm$fields` as List<KMutableProperty1<T, Any?>>

    val target = companion.`$realm$primaryKey`?.let { primaryKey ->
        create(mediator, realm, instance::class, (primaryKey as KProperty1<T, Any?>).get(instance))
    } ?: create(mediator, realm, instance::class)
    cache[instance] = target as RealmObjectInternal
    // TODO OPTIMIZE We could set all properties at once with on C-API call
    for (member: KMutableProperty1<T, Any?> in members) {
        val targetValue = member.get(instance).let { sourceObject ->
            // Check whether the source is a RealmObject, a primitive or a list
            // In case of list ensure the values from the source are passed to the native list

            if (sourceObject is RealmObjectInternal && !sourceObject.`$realm$IsManaged`) {
                cache.getOrPut(sourceObject) {
                    copyToRealm(mediator, realm, sourceObject, cache)
                }
            } else if (sourceObject is RealmList<*>) {
                processListMember(mediator, realm, cache, member, target, sourceObject)
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
    return target
}

private fun <T : RealmObject> processListMember(
    mediator: Mediator,
    realm: RealmReference,
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
                copyToRealm(mediator, realm, item, cache)
            }
            list.add(value)
        } else {
            list.add(item)
        }
    }
    return list
}

