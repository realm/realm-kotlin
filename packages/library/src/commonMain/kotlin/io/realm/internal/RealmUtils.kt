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

import io.realm.interop.RealmInterop
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModelInternal
import io.realm.runtimeapi.RealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

@Suppress("TooGenericExceptionCaught") // Remove when errors are properly typed in https://github.com/realm/realm-kotlin/issues/70
fun <T : RealmObject> create(schema: Mediator, realm: NativePointer, type: KClass<T>): T {
    // FIXME Does not work with obfuscation. We should probably supply the static meta data through
    //  the companion (accessible through schema) or might even have a cached version of the key in
    //  some runtime container of an open realm.
    //  https://github.com/realm/realm-kotlin/issues/85
    //  https://github.com/realm/realm-kotlin/issues/105
    val objectType = type.simpleName ?: error("Cannot get class name")
    try {
        val managedModel = schema.newInstance(type)
        val key = RealmInterop.realm_find_class(realm, objectType)
        return managedModel.manage(
            realm,
            schema,
            type,
            RealmInterop.realm_object_create(realm, key)
        )
    } catch (e: RuntimeException) {
        // FIXME Throw proper exception
        //  https://github.com/realm/realm-kotlin/issues/70
        @Suppress("TooGenericExceptionThrown")
        throw RuntimeException("Failed to create object of type '$objectType'", e)
    }
}

fun <T : RealmObject> copyToRealm(schema: Mediator, realm: NativePointer, o: T, cache: MutableMap<RealmModelInternal, RealmModelInternal> = mutableMapOf()): T {
    val realmObjectCompanion = schema.companionMapping[o::class] ?: error("Class is not part of the schema for this realm")
    val members: List<KMutableProperty1<T, Any?>> = realmObjectCompanion.fields as List<KMutableProperty1<T, Any?>>

    val target = create(schema, realm, o::class)
    cache[o as RealmModelInternal] = target as RealmModelInternal
    // TODO OPTIMIZE We could set all properties at once with on C-API call
    for (member: KMutableProperty1<T, Any?> in members) {
        val get = member.get(o).let { o ->
            if (o is RealmModelInternal && !o.`$realm$IsManaged`) {
                cache.getOrPut(o) { copyToRealm(schema, realm, o, cache) }
            } else {
                o
            }
        }
        get?.let {
            // TODO OPTIMIZE Should we do a separate setter that allows the isDefault flag for sync
            //  optimizations
            member.set(target, it)
        }
    }
    return target
}
