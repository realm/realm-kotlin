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
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_erase
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_find
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_get
import io.realm.kotlin.internal.interop.RealmInterop.realm_dictionary_insert
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_get
import io.realm.kotlin.internal.interop.RealmMapPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmResultsPointer
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmMap
import kotlin.reflect.KClass

// ----------------------------------------------------------------------
// Map
// ----------------------------------------------------------------------

internal abstract class ManagedRealmMap<K, V> constructor(
    internal val nativePointer: RealmMapPointer,
    val operator: MapOperator<K, V>
) : AbstractMutableMap<K, V>(), RealmMap<K, V> {

    override val entries: MutableSet<MutableMap.MutableEntry<K, V>> by lazy {
        operator.realmReference.checkClosed()
        RealmMapEntrySetImpl(nativePointer, operator)
    }

    override val keys: MutableSet<K>
        get() = operator.keys

    override val size: Int
        get() = operator.size

    override val values: MutableCollection<V> by lazy {
        operator.realmReference.checkClosed()
        val resultsPointer = RealmInterop.realm_dictionary_to_results(nativePointer)
        RealmMapValues(resultsPointer, operator)
    }

    override fun clear() = operator.clear()

    override fun containsKey(key: K): Boolean = operator.containsKey(key)

    override fun containsValue(value: V): Boolean = operator.containsValue(value)

    override fun get(key: K): V? = operator.get(key)

    override fun put(key: K, value: V): V? = operator.put(key, value)

    override fun remove(key: K): V? = operator.remove(key)

    internal fun isValid(): Boolean =
        !nativePointer.isReleased() && RealmInterop.realm_dictionary_is_valid(nativePointer)
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer. It is
 * used internally by [ManagedRealmMap], [RealmMapEntrySetImpl] and [ManagedRealmMapEntry].
 */
internal interface MapOperator<K, V> : CollectionOperator<V, RealmMapPointer> {

    // Modification counter used to detect concurrent writes from the iterator
    var modCount: Int
    val keyConverter: RealmValueConverter<K>
    override val nativePointer: RealmMapPointer

    val size: Int
        get() {
            realmReference.checkClosed()
            return RealmInterop.realm_dictionary_size(nativePointer).toInt()
        }

    val keys: MutableSet<K>
        get() {
            realmReference.checkClosed()
            return KeySet(nativePointer, this)
        }

    fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Pair<V?, Boolean>
    fun eraseInternal(key: K): Pair<V?, Boolean>
    fun getEntryInternal(position: Int): Pair<K, V>
    fun getInternal(key: K): V?
    fun containsValueInternal(value: V): Boolean

    // Compares two values. Byte arrays are compared structurally. Objects are only equal if the
    // memory address is the same.
    fun areValuesEqual(expected: V?, actual: V?): Boolean

    // This function returns a Pair because it is used by both the Map and the entry Set. Having
    // both different semantics, Map returns the previous value for the key whereas the entry Set
    // returns whether the element was inserted successfully.
    fun insert(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Pair<V?, Boolean> {
        realmReference.checkClosed()
        return insertInternal(key, value, updatePolicy, cache)
    }

    // Similarly to insert, Map returns the erased value whereas the entry Set returns whether the
    // element was erased successfully.
    fun erase(key: K): Pair<V?, Boolean> {
        realmReference.checkClosed()
        return eraseInternal(key)
    }

    fun getEntry(position: Int): Pair<K, V> {
        realmReference.checkClosed()
        return getEntryInternal(position)
    }

    fun get(key: K): V? {
        realmReference.checkClosed()
        return getInternal(key)
    }

    fun containsValue(value: V): Boolean {
        realmReference.checkClosed()
        return containsValueInternal(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun getValue(resultsPointer: RealmResultsPointer, index: Int): V? {
        return getterScope {
            with(valueConverter) {
                val transport = realm_results_get(resultsPointer, index.toLong())
                realmValueToPublic(transport)
            } as V
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getKey(resultsPointer: RealmResultsPointer, index: Int): K {
        return getterScope {
            with(keyConverter) {
                val transport = realm_results_get(resultsPointer, index.toLong())
                realmValueToPublic(transport)
            } as K
        }
    }

    fun put(
        key: K,
        value: V,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): V? {
        realmReference.checkClosed()
        return insertInternal(key, value, updatePolicy, cache).first
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
        return eraseInternal(key).first
            .also { modCount += 1 }
    }

    fun clear() {
        realmReference.checkClosed()
        RealmInterop.realm_dictionary_clear(nativePointer)
        modCount += 1
    }

    fun containsKey(key: K): Boolean {
        realmReference.checkClosed()

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core so we need to use an inputScope
        return inputScope {
            with(keyConverter) {
                RealmInterop.realm_dictionary_contains_key(nativePointer, publicToRealmValue(key))
            }
        }
    }
}

internal open class PrimitiveMapOperator<K, V> constructor(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<V>,
    override val keyConverter: RealmValueConverter<K>,
    override val nativePointer: RealmMapPointer
) : MapOperator<K, V> {

    override var modCount: Int = 0

    override fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
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
        }.also { modCount += 1 }
    }

    override fun eraseInternal(key: K): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            with(valueConverter) {
                realm_dictionary_erase(nativePointer, keyTransport).let {
                    Pair(realmValueToPublic(it.first), it.second)
                }
            }
        }.also { modCount += 1 }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntryInternal(position: Int): Pair<K, V> {
        return getterScope {
            realm_dictionary_get(nativePointer, position)
                .let {
                    val key = with(keyConverter) { realmValueToPublic(it.first) }
                    val value = with(valueConverter) { realmValueToPublic(it.second) }
                    Pair(key, value)
                } as Pair<K, V>
        }
    }

    override fun getInternal(key: K): V? {
        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core so we need to use an inputScope
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            val valueTransport = realm_dictionary_find(nativePointer, keyTransport)
            with(valueConverter) { realmValueToPublic(valueTransport) }
        }
    }

    override fun containsValueInternal(value: V): Boolean {
        // Even though we are getting a value we need to free the data buffers of the string values
        // we send down to Core so we need to use an inputScope
        return inputScope {
            with(valueConverter) {
                RealmInterop.realm_dictionary_contains_value(
                    nativePointer,
                    publicToRealmValue(value)
                )
            }
        }
    }

    override fun areValuesEqual(expected: V?, actual: V?): Boolean =
        when (expected) {
            is ByteArray -> expected.contentEquals(actual?.let { it as ByteArray })
            else -> expected == actual
        }
}

internal class RealmAnyMapOperator<K> constructor(
    mediator: Mediator,
    realmReference: RealmReference,
    valueConverter: RealmValueConverter<RealmAny?>,
    keyConverter: RealmValueConverter<K>,
    nativePointer: RealmMapPointer
) : PrimitiveMapOperator<K, RealmAny?>(
    mediator,
    realmReference,
    valueConverter,
    keyConverter,
    nativePointer
) {
    override fun containsValueInternal(value: RealmAny?): Boolean {
        // Unmanaged objects are never found in a managed dictionary
        if (value?.type == RealmAny.Type.OBJECT) {
            if (!value.asRealmObject<RealmObjectInternal>().isManaged()) return false
        }

        // Even though we are getting a value we need to free the data buffers of the string values
        // we send down to Core so we need to use an inputScope
        return inputScope {
            with(valueConverter) {
                RealmInterop.realm_dictionary_contains_value(
                    nativePointer,
                    publicToRealmValue(value)
                )
            }
        }
    }
}

internal class RealmObjectMapOperator<K, V> constructor(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<V>,
    override val keyConverter: RealmValueConverter<K>,
    override val nativePointer: RealmMapPointer,
    private val clazz: KClass<V & Any>
) : MapOperator<K, V> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun insertInternal(
        key: K,
        value: V?,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Pair<V?, Boolean> {
        return inputScope {
            val keyTransport = with(keyConverter) { publicToRealmValue(key) }
            val objTransport = realmObjectToRealmReferenceWithImport(
                value as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            ).let {
                realmObjectTransport(it as RealmObjectInterop?)
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
        }.also { modCount += 1 }
    }

    @Suppress("UNCHECKED_CAST")
    override fun eraseInternal(key: K): Pair<V?, Boolean> {
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
        }.also { modCount += 1 }
    }

    @Suppress("UNCHECKED_CAST")
    override fun getEntryInternal(position: Int): Pair<K, V> {
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
    override fun getInternal(key: K): V? {
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

    override fun containsValueInternal(value: V): Boolean {
        value?.also {
            // Unmanaged objects are never found in a managed dictionary
            if (!(it as RealmObjectInternal).isManaged()) return false
        }

        // Even though we are getting a value we need to free the data buffers of the string we
        // send down to Core so we need to use an inputScope
        return inputScope {
            with(valueConverter) {
                RealmInterop.realm_dictionary_contains_value(
                    nativePointer,
                    publicToRealmValue(value)
                )
            }
        }
    }

    override fun areValuesEqual(expected: V?, actual: V?): Boolean {
        // Two objects are only the same if they point to the same memory address
        if (expected === actual) return true
        return false
    }
}

// ----------------------------------------------------------------------
// Dictionary
// ----------------------------------------------------------------------

internal class UnmanagedRealmDictionary<E>(
    dictionary: Map<String, E> = mutableMapOf()
) : RealmDictionary<E>, MutableMap<String, E> by dictionary.toMutableMap()

internal class ManagedRealmDictionary<E> constructor(
    nativePointer: RealmMapPointer,
    operator: MapOperator<String, E>
) : ManagedRealmMap<String, E>(nativePointer, operator), RealmDictionary<E>

// ----------------------------------------------------------------------
// Keys
// ----------------------------------------------------------------------

/**
 * [MutableSet] containing all the keys present in a dictionary. Core returns keys as results.
 */
internal class KeySet<K>(
    nativePointer: RealmMapPointer,
    private val operator: MapOperator<K, *>
) : AbstractMutableSet<K>() {

    private val keysPointer = RealmInterop.realm_dictionary_get_keys(nativePointer)

    override val size: Int
        get() = RealmInterop.realm_results_count(keysPointer).toInt()

    override fun add(element: K): Boolean =
        throw UnsupportedOperationException("Adding keys to a dictionary through 'dictionary.keys' is not allowed.")

    override fun iterator(): MutableIterator<K> =
        object : RealmMapGenericIterator<K, K>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): K = operator.getKey(keysPointer, position)
        }
}

// ----------------------------------------------------------------------
// Values
// ----------------------------------------------------------------------

/**
 * The semantics of [MutableMap.values] establish a connection between these values and the map
 * itself. This collection represents the map's values as a [MutableCollection] of [V] values.
 *
 * The default implementation of `MutableMap.values` in Kotlin allows removals but no additions -
 * which makes sense since keys are nowhere to be found in this data structure.
 *
 * The implementation uses `realm_dictionary_to_results` internally, which (surprisingly) returns a
 * `realm_results_t` struct. Since the current `RealmResults` implementation is bound by
 * `RealmObject` we cannot use them to contain a map's values since maps of primitive values are
 * also supported. A separate implementation these `realm_results_t` was chosen over adapting the
 * current results infrastructure since the collection must be mutable too, and the current results
 * implementation is not.
 */
internal class RealmMapValues<K, V> constructor(
    private val resultsPointer: RealmResultsPointer,
    private val operator: MapOperator<K, V>
) : AbstractMutableCollection<V>() {

    override val size: Int
        get() = operator.size

    override fun add(element: V): Boolean =
        throw UnsupportedOperationException("Adding values to a dictionary through 'dictionary.values' is not allowed.")

    override fun addAll(elements: Collection<V>): Boolean =
        throw UnsupportedOperationException("Adding values to a dictionary through 'dictionary.values' is not allowed.")

    override fun clear() = operator.clear()

    override fun iterator(): MutableIterator<V> =
        object : RealmMapGenericIterator<K, V>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): V =
                operator.getValue(resultsPointer, position) as V
        }

    // Custom implementation to allow removal of byte arrays based on structural equality
    @Suppress("ReturnCount")
    override fun remove(element: V): Boolean {
        val it = iterator()
        if (element == null) {
            while (it.hasNext()) {
                if (it.next() == null) {
                    it.remove()
                    return true
                }
            }
        } else {
            while (it.hasNext()) {
                if (operator.areValuesEqual(element, it.next())) {
                    it.remove()
                    return true
                }
            }
        }
        return false
    }

    // Custom implementation to allow removal of byte arrays based on structural equality
    override fun removeAll(elements: Collection<V>): Boolean =
        elements.fold(false) { accumulator, value ->
            remove(value) or accumulator
        }

    // Custom implementation to allow removal of byte arrays based on structural equality
    @Suppress("NestedBlockDepth")
    override fun retainAll(elements: Collection<V>): Boolean {
        var modified = false
        val it = iterator()
        while (it.hasNext()) {
            val next = it.next()
            if (next is ByteArray) {
                val otherIterator = elements.iterator()
                while (otherIterator.hasNext()) {
                    val otherNext = otherIterator.next()
                    if (!next.contentEquals(otherNext as ByteArray?)) {
                        it.remove()
                        modified = true
                        continue // Avoid looping on an already deleted element
                    }
                }
            } else {
                if (!elements.contains(next)) {
                    it.remove()
                    modified = true
                }
            }
        }
        return modified
    }
}

// ----------------------------------------------------------------------
// Iterator
// ----------------------------------------------------------------------

/**
 * Base iterator used by [RealmDictionary.keys], [RealmDictionary.values] and
 * [RealmDictionary.entries]. Upon calling [next] the iterator used by `keys` returns a [K],
 * `entries` returns a [MutableMap.MutableEntry] whereas the one used by `values` returns a [T].
 */
internal abstract class RealmMapGenericIterator<K, T>(
    protected val operator: MapOperator<K, *>
) : MutableIterator<T> {

    private var expectedModCount = operator.modCount // Current modifications in the map
    private var cursor = 0 // The position returned by next()
    private var lastReturned = -1 // The last known returned position

    abstract fun getNext(position: Int): T

    override fun hasNext(): Boolean {
        operator.realmReference.checkClosed()
        checkConcurrentModification()

        return cursor < operator.size
    }

    override fun remove() {
        operator.realmReference.checkClosed()
        checkConcurrentModification()

        if (operator.size == 0) {
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
        expectedModCount = operator.modCount
        if (!erased) {
            throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
        }
    }

    override fun next(): T {
        operator.realmReference.checkClosed()
        checkConcurrentModification()

        val position = cursor
        if (position >= operator.size) {
            throw IndexOutOfBoundsException("Cannot access index $position when size is ${operator.size}. Remember to check hasNext() before using next().")
        }
        val next = getNext(position)
        lastReturned = position
        cursor = position + 1
        return next
    }

    private fun checkConcurrentModification() {
        if (operator.modCount != expectedModCount) {
            throw ConcurrentModificationException("The underlying RealmDictionary was modified while iterating over its entry set.")
        }
    }
}

// ----------------------------------------------------------------------
// Entry set
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

    override fun iterator(): MutableIterator<MutableMap.MutableEntry<K, V>> =
        object : RealmMapGenericIterator<K, MutableMap.MutableEntry<K, V>>(operator) {
            @Suppress("UNCHECKED_CAST")
            override fun getNext(position: Int): MutableMap.MutableEntry<K, V> {
                val pair = operator.getEntry(position)
                return ManagedRealmMapEntry(pair.first, operator) as MutableMap.MutableEntry<K, V>
            }
        }

    override fun remove(element: MutableMap.MutableEntry<K, V>): Boolean =
        operator.get(element.key).let { value ->
            when (operator.areValuesEqual(value, element.value)) {
                true -> operator.erase(element.key).second
                false -> false
            }
//            when {
//                element.value != value -> false
//                else -> operator.erase(element.key).second
//            }
        }

    override fun removeAll(elements: Collection<MutableMap.MutableEntry<K, V>>): Boolean =
        elements.fold(false) { accumulator, entry ->
            remove(entry) or accumulator
        }
}

/**
 * Naive implementation of [MutableMap.MutableEntry] for adding new elements to a [RealmMap] via the
 * [RealmMapEntrySet] produced by `RealmMap.entries`.
 */
internal class UnmanagedRealmMapEntry<K, V> constructor(
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

        // Byte arrays are compared at a structural level
        if (this.value is ByteArray && other.value is ByteArray) {
            val thisByteArray = this.value as ByteArray
            val otherByteArray = other.value as ByteArray
            if (this.key == other.key && thisByteArray.contentEquals(otherByteArray)) {
                return true
            }
            return false
        }

        return (this.key == other.key) && (this.value == other.value)
    }
}

/**
 * Implementation of a managed [MutableMap.MutableEntry] returned by the [Iterator] from a
 * [ManagedRealmMap] [RealmMapEntrySet]. It is possible to modify the [value] of the entry. Doing so
 * results in the managed `RealmMap` being updated as well.
 */
internal class ManagedRealmMapEntry<K, V> constructor(
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

        // Byte arrays are compared at a structural level
        if (this.value is ByteArray && other.value is ByteArray) {
            val thisByteArray = this.value as ByteArray
            val otherByteArray = other.value as ByteArray
            if (this.key == other.key && thisByteArray.contentEquals(otherByteArray)) {
                return true
            }
            return false
        }

        return (this.key == other.key) && (this.value == other.value)
    }
}

// ----------------------------------------------------------------------
// Internal type alias and helpers for factory functions
// ----------------------------------------------------------------------

internal typealias RealmMapEntrySet<K, V> = MutableSet<MutableMap.MutableEntry<K, V>>

internal typealias RealmMapMutableEntry<K, V> = MutableMap.MutableEntry<K, V>

internal fun <K, V> realmMapEntryOf(pair: Pair<K, V>): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(pair.first, pair.second)

internal fun <K, V> realmMapEntryOf(key: K, value: V): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(key, value)

internal fun <K, V> realmMapEntryOf(entry: Map.Entry<K, V>): RealmMapMutableEntry<K, V> =
    UnmanagedRealmMapEntry(entry.key, entry.value)

internal fun <T> Map<String, T>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }

internal fun <T> Array<out Pair<String, T>>.asRealmDictionary(): RealmDictionary<T> =
    UnmanagedRealmDictionary<T>().apply { putAll(this@asRealmDictionary) }
