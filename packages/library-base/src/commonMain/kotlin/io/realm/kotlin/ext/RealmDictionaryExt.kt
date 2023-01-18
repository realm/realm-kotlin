/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.ext

import io.realm.kotlin.internal.UnmanagedRealmDictionary
import io.realm.kotlin.internal.asRealmDictionary
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmDictionaryMutableEntry
import io.realm.kotlin.types.RealmMapMutableEntry

/**
 * Instantiates an **unmanaged** [RealmDictionary] from a variable number of [Pair]s of [String]
 * and [T].
 */
public fun <T> realmDictionaryOf(vararg elements: Pair<String, T>): RealmDictionary<T> =
    if (elements.isNotEmpty()) elements.asRealmDictionary() else UnmanagedRealmDictionary()

/**
 * Instantiates an **unmanaged** [RealmDictionary] from a [Collection] of [Pair]s of [String] and
 * [T].
 */
public fun <T> realmDictionaryOf(elements: Collection<Pair<String, T>>): RealmDictionary<T> =
    if (elements.isNotEmpty()) {
        elements.toTypedArray().asRealmDictionary()
    } else {
        UnmanagedRealmDictionary()
    }

/**
 * Instantiates an **unmanaged** [RealmMapMutableEntry] from a [Pair] of [K] and [V]. Entries are
 * used by the entry set produced by [RealmDictionary.entries]. It is possible to update the entry
 * which will result in the underlying [RealmDictionary] to be updated too.
 */
@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
public fun <V> realmDictionaryEntryOf(pair: Pair<String, V>): RealmDictionaryMutableEntry<V> =
    TODO("Not yet implemented")

/**
 * Instantiates an **unmanaged** [RealmMapMutableEntry] from a [key]-[value] pair. Entries are used
 * by the entry set produced by [RealmDictionary.entries]. It is possible to update the entry
 * which will result in the underlying [RealmDictionary] to be updated too.
 */
@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
public fun <V> realmDictionaryEntryOf(key: String, value: V): RealmDictionaryMutableEntry<V> =
    TODO("Not yet implemented")

/**
 * Instantiates an **unmanaged** [RealmMapMutableEntry] from another [Map.Entry]. Entries are used
 * by the entry set produced by [RealmDictionary.entries]. It is possible to update the entry
 * which will result in the underlying [RealmDictionary] to be updated too.
 */
@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
public fun <V> realmDictionaryEntryOf(entry: Map.Entry<String, V>): RealmDictionaryMutableEntry<V> =
    TODO("Not yet implemented")

// TODO add support for RealmDictionary<T>.copyFromRealm()
// /**
//  * Makes an unmanaged in-memory copy of the elements in a managed [RealmDictionary]. This is a deep
//  * copy that will copy all referenced objects.
//  *
//  * @param depth limit of the deep copy. All object references after this depth will be `null`.
//  * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
//  * @returns an in-memory copy of all input objects.
//  * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
//  */
// public inline fun <T : RealmObject> RealmDictionary<T>.copyFromRealm(
//     depth: UInt = UInt.MAX_VALUE
// ): Set<T> {
//     TODO()
// }
