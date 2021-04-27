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
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

/**
 * Add a check and error message for code that never be reached because it should have been
 * replaced by the Compiler Plugin.
 */
@Suppress("FunctionNaming")
internal inline fun REPLACED_BY_IR(
    message: String = "This code should have been replaced by the Realm Compiler Plugin. " +
        "Has the `realm-kotlin` Gradle plugin been applied to the project?"
): Nothing = throw AssertionError(message)

@Suppress("TooGenericExceptionCaught") // Remove when errors are properly typed in https://github.com/realm/realm-kotlin/issues/70
fun <T : RealmObject> create(mediator: Mediator, realmPointer: NativePointer, type: KClass<T>): T {
    // FIXME Does not work with obfuscation. We should probably supply the static meta data through
    //  the companion (accessible through schema) or might even have a cached version of the key in
    //  some runtime container of an open realm.
    //  https://github.com/realm/realm-kotlin/issues/85
    //  https://github.com/realm/realm-kotlin/issues/105
    val objectType = type.simpleName ?: error("Cannot get class name")
    try {
        val managedModel = mediator.createInstanceOf(type)
        val key = RealmInterop.realm_find_class(realmPointer, objectType)
        return managedModel.manage(
            realmPointer,
            mediator,
            type,
            RealmInterop.realm_object_create(realmPointer, key)
        )
    } catch (e: RuntimeException) {
        // FIXME Throw proper exception
        //  https://github.com/realm/realm-kotlin/issues/70
        @Suppress("TooGenericExceptionThrown")
        throw RuntimeException("Failed to create object of type '$objectType'", e)
    }
}

fun <T : RealmObject> copyToRealm(mediator: Mediator, realmPointer: NativePointer, instance: T, cache: MutableMap<RealmModelInternal, RealmModelInternal> = mutableMapOf()): T {
    // Copying already managed instance is an no-op
    if ((instance as RealmModelInternal).`$realm$IsManaged`) return instance

    val members = mediator.companionOf(instance::class).`$realm$fields` as List<KMutableProperty1<T, Any?>>

    val target = create(mediator, realmPointer, instance::class)
    cache[instance] = target as RealmModelInternal
    // TODO OPTIMIZE We could set all properties at once with on C-API call
    for (member: KMutableProperty1<T, Any?> in members) {
        val targetValue = member.get(instance).let { sourceObject ->
            if (sourceObject is RealmModelInternal && !sourceObject.`$realm$IsManaged`) {
                cache.getOrPut(sourceObject) { copyToRealm(mediator, realmPointer, sourceObject, cache) }
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
