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
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_erase
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_find
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_get
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_insert
import io.realm.kotlin.internal.interop.RealmMapPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.types.BaseRealmObject
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

    override fun get(key: K): V? = operator.get(key)

    override fun put(key: K, value: V): V? = operator.put(key, value)

    override fun remove(key: K): V? = operator.remove(key)
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer. It is
 * used internally by [ManagedRealmMap], [RealmMapEntrySetImpl] and [ManagedRealmMapEntry].
 */
internal interface MapOperator<K, V> : CollectionOperator<V, RealmMapPointer> {

    val keyConverter: RealmValueConverter<K>
    override val nativePointer: RealmMapPointer

    val size: Int
        get() {
            realmReference.checkClosed()
            return RealmInterop.realm_dictionary_size(nativePointer).toInt()
        }

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
        realmReference.checkClosed()
        return insert(key, value).first
    }

    fun putAll(
        from: Map<out K, V>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ) {
        realmReference.checkClosed()
        for (entry in from) {
            put(entry.key, entry.value, updatePolicy, cache)
        }
    }

    fun remove(key: K): V? {
        realmReference.checkClosed()
        return erase(key).first
    }

    fun clear() {
        realmReference.checkClosed()
        RealmInterop.realm_dictionary_clear(nativePointer)
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
        realmReference.checkClosed()
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            with(valueConverter) {
                val valueTransport = publicToRealmValue(value)
                realm_dictionary_insert(
                    nativePointer,
                    keyTransport,
                    valueTransport
                ).let {
                    Pair(realmValueToPublic(it.first), it.second)
                }
            }
        }
    }

    override fun erase(key: K): Pair<V?, Boolean> {
        realmReference.checkClosed()
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            with(valueConverter) {
                realm_dictionary_erase(nativePointer, keyTransport).let {
                    Pair(realmValueToPublic(it.first), it.second)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntry(position: Int): Pair<K, V> {
        realmReference.checkClosed()
        return getterScope {
            realm_dictionary_get(nativePointer, position)
                .let {
                    val key = with(keyConverter) { realmValueToPublic(it.first) }
                    val value = with(valueConverter) { realmValueToPublic(it.second) }
                    Pair(key, value)
                } as Pair<K, V>
        }
    }

    override fun get(key: K): V? {
        realmReference.checkClosed()

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core so we need to use an inputScope
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            val valueTransport = realm_dictionary_find(nativePointer, keyTransport)
            with(valueConverter) { realmValueToPublic(valueTransport) }
        }
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
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            val objTransport = realmObjectToRealmReferenceWithImport(
                value as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            ).let {
                realmObjectTransport(it as RealmObjectInterop)
            }
            realm_dictionary_insert(
                nativePointer,
                keyTransport,
                objTransport
            ).let {
                val previousObject = realmValueToRealmObject(
                    it.first,
                    clazz as KClass<out BaseRealmObject>,
                    mediator,
                    realmReference
                )
                Pair(previousObject, it.second)
            } as Pair<V?, Boolean>
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun erase(key: K): Pair<V?, Boolean> {
        realmReference.checkClosed()
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            realm_dictionary_erase(nativePointer, keyTransport).let {
                val previousObject = realmValueToRealmObject(
                    it.first,
                    clazz as KClass<out BaseRealmObject>,
                    mediator,
                    realmReference
                )
                Pair(previousObject, it.second)
            } as Pair<V?, Boolean>
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntry(position: Int): Pair<K, V> {
        realmReference.checkClosed()
        return getterScope {
            realm_dictionary_get(nativePointer, position)
                .let {
                    val key = with(keyConverter) { realmValueToPublic(it.first) }
                    val value = realmValueToRealmObject(
                        it.second,
                        clazz as KClass<out BaseRealmObject>,
                        mediator,
                        realmReference
                    )
                    Pair(key, value)
                } as Pair<K, V>
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(key: K): V? {
        realmReference.checkClosed()

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core so we need to use an inputScope
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            realmValueToRealmObject(
                realm_dictionary_find(nativePointer, keyTransport),
                clazz as KClass<out BaseRealmObject>,
                mediator,
                realmReference
            )
        } as V?
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
