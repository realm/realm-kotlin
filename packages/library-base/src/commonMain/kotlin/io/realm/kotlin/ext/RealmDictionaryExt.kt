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

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.ManagedRealmDictionary
import io.realm.kotlin.internal.ManagedRealmMap
import io.realm.kotlin.internal.RealmMapMutableEntry
import io.realm.kotlin.internal.UnmanagedRealmDictionary
import io.realm.kotlin.internal.asRealmDictionary
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.query
import io.realm.kotlin.internal.realmMapEntryOf
import io.realm.kotlin.notifications.MapChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.TRUE_PREDICATE
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmDictionaryMutableEntry
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmMap
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.flow.Flow

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
 * Instantiates an **unmanaged** [RealmDictionaryMutableEntry] from a [Pair] of [String] and [V]
 * that can be added to an entry set produced by [RealmDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Realm,
 * updating the underlying [RealmDictionary].
 */
public fun <V> realmDictionaryEntryOf(pair: Pair<String, V>): RealmDictionaryMutableEntry<V> =
    realmMapEntryOf(pair)

/**
 * Instantiates an **unmanaged** [RealmMapMutableEntry] from a [key]-[value] pair
 * that can be added to an entry set produced by [RealmDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Realm,
 * updating the underlying [RealmDictionary].
 */
public fun <V> realmDictionaryEntryOf(key: String, value: V): RealmDictionaryMutableEntry<V> =
    realmMapEntryOf(key, value)

/**
 * Instantiates an **unmanaged** [RealmMapMutableEntry] from another [Map.Entry]
 * that can be added to an entry set produced by [RealmDictionary.entries]. It is possible to add an
 * unmanaged entry to a dictionary entry set. This will result in the entry being copied to Realm,
 * updating the underlying [RealmDictionary].
 */
public fun <V> realmDictionaryEntryOf(entry: Map.Entry<String, V>): RealmDictionaryMutableEntry<V> =
    realmMapEntryOf(entry)

/**
 * Makes an unmanaged in-memory copy of the elements in a managed [RealmDictionary]. This is a deep
 * copy that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList], [RealmSet] and [RealmDictionary] variables containing objects will be empty.
 * Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <T : RealmObject> RealmDictionary<T?>.copyFromRealm(
    depth: UInt = UInt.MAX_VALUE
): Map<String, T?> {
    return this.getRealm<TypedRealm>()
        ?.copyFromRealm(this, depth)
        ?: throw IllegalArgumentException("This RealmDictionary is unmanaged. Only managed dictionaries can be copied.")
}

/**
 * Query the objects in a dictionary by `filter` and `arguments`. The query is launched against the
 * output obtained from [RealmDictionary.values]. This means keys are not taken into consideration.
 *
 * @param filter the Realm Query Language predicate to append.
 * @param arguments Realm values for the predicate.
 */
public fun <T : BaseRealmObject> RealmDictionary<T?>.query(
    filter: String = TRUE_PREDICATE,
    vararg arguments: Any?
): RealmQuery<T> =
    if (this is ManagedRealmDictionary) {
        query(filter, arguments)
    } else {
        throw IllegalArgumentException("Unmanaged dictionary values cannot be queried.")
    }

/**
 * TODO
 */
public fun <K : String, T : BaseRealmObject> RealmMap<K, T>.asFlow(keyPaths: List<String>? = null): Flow<MapChange<K, T>> {
    if (this is ManagedRealmMap) {
        operator.realmReference.checkClosed()
        // TODO Find Class Key
        return operator.realmReference.owner.registerObserver(this, Pair(ClassKey(0), keyPaths))
    } else {
        throw UnsupportedOperationException("Unmanaged maps cannot be observed.")
    }
}

/**
 * TODO
 */
public fun <T : BaseRealmObject> RealmDictionary<T>.asFlow(keyPaths: List<String>? = null): Flow<MapChange<String, T>> {
    if (this is ManagedRealmDictionary) {
        operator.realmReference.checkClosed()
        // TODO Find Class Key
        return operator.realmReference.owner.registerObserver(this, Pair(ClassKey(0), keyPaths))
    } else {
        throw UnsupportedOperationException("Unmanaged dictionaries cannot be observed.")
    }
}
