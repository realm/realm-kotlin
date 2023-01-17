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

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.interop.RealmMapPointer
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmMap
import kotlin.reflect.KClass

// ----------------------------------------------------------------------
// Map
// ----------------------------------------------------------------------

internal abstract class ManagedRealmMap<K, V>(
    internal val nativePointer: RealmMapPointer,
    val operator: MapOperator<K, V>
) : AbstractMutableMap<K, V>(), RealmMap<K, V> {

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>>
        get() = TODO("Not yet implemented")

    // TODO missing support for Results Dictionary::get_keys() in C-API: https://github.com/realm/realm-core/issues/6181
    override val keys: MutableSet<K>
        get() = TODO("Not yet implemented")

    override val size: Int
        get() = operator.size

    // TODO update RealmResults to hold primitive values
    override val values: MutableCollection<V>
        get() = TODO("Not yet implemented")

    override fun clear() = operator.clear()

    override fun containsKey(key: K): Boolean {
        // TODO missing support for Dictionary::contains(StringData key) in C-API: https://github.com/realm/realm-core/issues/6181
        TODO("Not yet implemented")
    }

    override fun containsValue(value: V): Boolean {
        // TODO missing support for Dictionary::find_any(Mixed value) in C-API: https://github.com/realm/realm-core/issues/6181
        TODO("Not yet implemented")
    }

    override fun get(key: K): V? = TODO("Not yet implemented")

    override fun put(key: K, value: V): V? = TODO("Not yet implemented")

    override fun remove(key: K): V? = TODO("Not yet implemented")
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer. It is
 * used internally by [ManagedRealmMap], [RealmMapEntrySetImpl] and [ManagedRealmMapEntry].
 */
internal interface MapOperator<K, V> : CollectionOperator<V, RealmMapPointer> {

    val keyConverter: RealmValueConverter<K>
    override val nativePointer: RealmMapPointer

    val size: Int
        get() = TODO("Not yet implemented")

    // This function returns a Pair because it is used by both the Map and the entry Set. Having
    // both different semantics, Map returns the previous value for the key whereas the entry Set
    // returns whether the element was inserted successfully.
    fun insert(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Pair<V?, Boolean>

    // Similarly to insert, Map returns the erased value whereas the entry Set returns whether the
    // element was erased successfully.
    fun erase(key: K): Pair<V?, Boolean>
    fun getEntry(position: Int): Pair<K, V>
    fun get(key: K): V?

    fun put(
        key: K,
        value: V,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): V? {
        TODO("Not yet implemented")
    }

    fun putAll(
        from: Map<out K, V>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        TODO("Not yet implemented")
    }

    fun remove(key: K): V? {
        TODO("Not yet implemented")
    }

    fun clear() {
        TODO("Not yet implemented")
    }
}

internal class PrimitiveMapOperator<K, V>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<V>,
    override val keyConverter: RealmValueConverter<K>,
    override val nativePointer: RealmMapPointer
) : MapOperator<K, V> {

    override fun insert(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        // TODO call realm_dictionary_insert when ready and return a Pair with 'previousValue' and 'inserted'
        TODO("Not yet implemented")
    }

    override fun erase(key: K): Pair<V?, Boolean> {
        realmReference.checkClosed()
        // TODO call realm_dictionary_erase when ready and return a Pair with 'previousValue' and 'erased'
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntry(position: Int): Pair<K, V> {
        realmReference.checkClosed()
        // TODO call realm_dictionary_get when ready and return a Pair with 'key' and 'value'
        TODO("Not yet implemented")
    }

    override fun get(key: K): V? {
        realmReference.checkClosed()
        // TODO call realm_dictionary_find when ready and return 'value'
        TODO("Not yet implemented")
    }
}

@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
internal class RealmObjectMapOperator<K, V>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<V>,
    override val keyConverter: RealmValueConverter<K>,
    override val nativePointer: RealmMapPointer,
    private val clazz: KClass<V & Any>
) : MapOperator<K, V> {

    @Suppress("UNCHECKED_CAST")
    override fun insert(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        realmReference.checkClosed()
        // TODO call realm_dictionary_insert when ready and return a Pair with 'previousValue' and 'inserted'
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun erase(key: K): Pair<V?, Boolean> {
        realmReference.checkClosed()
        // TODO call realm_dictionary_erase when ready and return a Pair with 'previousValue' and 'erased'
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntry(position: Int): Pair<K, V> {
        realmReference.checkClosed()
        // TODO call realm_dictionary_get when ready and return a Pair with 'key' and 'value'
        TODO("Not yet implemented")
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(key: K): V? {
        realmReference.checkClosed()

        // TODO call realm_dictionary_find when ready and return 'value'
        TODO("Not yet implemented")
    }
}

// ----------------------------------------------------------------------
// Dictionary
// ----------------------------------------------------------------------

internal class UnmanagedRealmDictionary<E> : RealmDictionary<E>, MutableMap<String, E> by mutableMapOf()

internal class ManagedRealmDictionary<E>(
    nativePointer: RealmMapPointer,
    operator: MapOperator<String, E>
) : ManagedRealmMap<String, E>(nativePointer, operator), RealmDictionary<E>

// ----------------------------------------------------------------------
// EntrySet
// ----------------------------------------------------------------------

// TODO add RealmMapEntrySetImpl: represents a ManagedRealmMap in the form of an
//  AbstractMutableSet<MutableMap.MutableEntry<K, V>>(). It is also a managed data structure.

// TODO add UnmanagedRealmMapEntry: represents unmanaged entries, used to add unmanaged K-V pairs to
//  a managed or unmanaged dictionary when working with from dictionary.entries.

// TODO add ManagedRealmMapEntry: represents managed entries obtained when iterating through the
//  values contained in a (managed) dictionary.entries.

// ----------------------------------------------------------------------
// Internal helpers for factory functions
// ----------------------------------------------------------------------

internal fun <T> Map<String, T>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }

internal fun <T> Array<out Pair<String, T>>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }
