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

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy {
        RealmMapEntrySetImpl(nativePointer, operator)
    }

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

/**
 * This class implements the typealias [RealmMapEntrySet] which matches
 * `MutableSet<MutableMap.MutableEntry<K, V>>`. This class allows operating on a [ManagedRealmMap]
 * in the form of a [Set] of [MutableMap.MutableEntry] values.
 *
 * Deletions are supported by the default semantics in JVM and K/N. These two operations are
 * equivalent:
 * ```
 * dictionary.remove(myKey)
 * dictionary.entries.remove(myKey, myValue) // implies we know the value of myValue
 * ```
 *
 * Default semantics forbid addition operations though. This is due to `AbstractCollection` not
 * having implemented this functionality both in JVM and K/N:
 * ```
 * dictionary.entries.add(SimpleEntry(myKey, myValue)) // throws UnsupportedOperationException
 * ```
 *
 * However, these semantics don't pose a problem for `RealmMap`s. The [add] function behaves in the
 * same way [RealmDictionary.put] does:
 * ```
 * // these two operations are equivalent and result in [myKey, myValue] being added to dictionary
 * dictionary[myKey] = myValue
 * dictionary.entries.add(realmMapEntryOf(myKey, myValue))
 * ```
 *
 * All other [Map] operations are funneled through the corresponding [MapOperator] and are available
 * from this class. Some of these operations leverage default implementations in
 * [AbstractMutableSet].
 */
internal class RealmMapEntrySetImpl<K, V> constructor(
    private val nativePointer: RealmMapPointer,
    private val operator: MapOperator<K, V>
) : AbstractMutableSet<MutableMap.MutableEntry<K, V>>(), RealmMapEntrySet<K, V> {

    override val size: Int
        get() = RealmInterop.realm_dictionary_size(nativePointer).toInt()

    override fun add(element: MutableMap.MutableEntry<K, V>): Boolean =
        operator.insert(element.key, element.value).second

    override fun addAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        elements.fold(false) { accumulator, entry ->
            (operator.insert(entry.key, entry.value).second) or accumulator
        }

    override fun clear() = operator.clear()

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> {
        // TODO how to handle concurrent modifications?
        return object : MutableIterator<MutableMap.MutableEntry<K, V>> {

            private var cursor = 0 // The position returned by next()
            private var lastReturned = -1 // The last known returned position

            override fun hasNext(): Boolean = cursor < operator.size

            @Suppress("UNCHECKED_CAST")
            override fun next(): MutableMap.MutableEntry<K, V> {
                val position = cursor
                if (position >= size) {
                    throw IndexOutOfBoundsException("Cannot access index $position when size is ${operator.size}. Remember to check hasNext() before using next().")
                }
                val entry = operator.getEntry(position)
                lastReturned = position
                cursor = position + 1
                return ManagedRealmMapEntry(
                    entry.first,
                    operator
                ) as MutableMap.MutableEntry<K, V>
            }

            override fun remove() {
                if (isEmpty()) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }
                if (lastReturned < 0) {
                    throw IllegalStateException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }

                val erased = getterScope {
                    val keyValuePair = operator.getEntry(lastReturned)
                    operator.erase(keyValuePair.first)
                        .second
                        .also {
                            if (lastReturned < cursor) {
                                cursor -= 1
                            }
                            lastReturned = -1
                        }
                }
                if (!erased) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
                }
            }
        }
    }

    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
        operator.erase(element.key).second

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        elements.fold(false) { accumulator, entry ->
            (operator.erase(entry.key).second) or accumulator
        }
}

/**
 * Naive implementation of [MutableMap.MutableEntry] for adding new elements to a [RealmMap] via the
 * [RealmMapEntrySet] produced by `RealmMap.entries`.
 */
internal class UnmanagedRealmMapEntry<K, V>(
    override val key: K,
    value: V
) : MutableMap.MutableEntry<K, V> {

    private var _value = value

    override val value: V
        get() = _value

    override fun setValue(newValue: V): V {
        val oldValue = this._value
        this._value = newValue
        return oldValue
    }

    override fun toString(): String = "UnmanagedRealmMapEntry{$key,$value}"
    override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false
        return (other.key == other.key) && (other.value == other.value)
    }
}

/**
 * Implementation of a managed [MutableMap.MutableEntry] returned by the [Iterator] from a
 * [ManagedRealmMap] [RealmMapEntrySet]. It is possible to modify the [value] of the entry. Doing so
 * results in the managed `RealmMap` being updated as well.
 */
internal class ManagedRealmMapEntry<K, V>(
    override val key: K,
    private val operator: MapOperator<K, V>
) : MutableMap.MutableEntry<K, V?> {

    override val value: V?
        get() = operator.get(key)

    override fun setValue(newValue: V?): V? {
        val previousValue = operator.get(key)
        operator.insert(key, newValue)
        return previousValue
    }

    override fun toString(): String = "ManagedRealmMapEntry{$key,$value}"
    override fun hashCode(): Int = (key?.hashCode() ?: 0) xor (value?.hashCode() ?: 0)
    override fun equals(other: Any?): Boolean {
        if (other !is Map.Entry<*, *>) return false
        return (other.key == other.key) && (other.value == other.value)
    }
}

// ----------------------------------------------------------------------
// Internal type alias and helpers for factory functions
// ----------------------------------------------------------------------

internal typealias RealmMapEntrySet<K, V> = MutableSet<MutableMap.MutableEntry<K, V>>

internal typealias RealmMapMutableEntry<K, V> = MutableMap.MutableEntry<K, V>

internal fun <K, V> realmMapEntryOf(pair: Pair<K, V>): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(pair.first, pair.second)

@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
internal fun <K, V> realmMapEntryOf(key: K, value: V): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(key, value)

@Suppress("UnusedPrivateMember") // TODO remove when parameter is used
internal fun <K, V> realmMapEntryOf(entry: Map.Entry<K, V>): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(entry.key, entry.value)

internal fun <T> Map<String, T>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }

internal fun <T> Array<out Pair<String, T>>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }
