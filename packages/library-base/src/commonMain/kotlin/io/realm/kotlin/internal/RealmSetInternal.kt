/*
 * Copyright 2022 Realm Inc.
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
import io.realm.kotlin.Versioned
import io.realm.kotlin.internal.RealmValueArgumentConverter.convertToQueryArgs
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_get
import io.realm.kotlin.internal.interop.RealmKeyPathArrayPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.internal.query.ObjectBoundQuery
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.internal.DeletedSetImpl
import io.realm.kotlin.notifications.internal.InitialSetImpl
import io.realm.kotlin.notifications.internal.UpdatedSetImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmSet
import io.realm.kotlin.types.RealmTypeAdapter
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged sets, backed by a [MutableSet].
 */
internal class UnmanagedRealmSet<E>(
    private val backingSet: MutableSet<E> = mutableSetOf()
) : RealmSet<E>, InternalDeleteable, MutableSet<E> by backingSet {
    override fun asFlow(keyPaths: List<String>?): Flow<SetChange<E>> {
        throw UnsupportedOperationException("Unmanaged sets cannot be observed.")
    }

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }

    override fun toString(): String = "UnmanagedRealmSet{${joinToString()}}"

    override fun equals(other: Any?): Boolean = backingSet == other

    override fun hashCode(): Int = backingSet.hashCode()
}

/**
 * Implementation for managed sets, backed by Realm.
 */
internal class ManagedRealmSet<E> constructor(
    internal val parent: RealmObjectReference<*>,
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E>, InternalDeleteable, CoreNotifiable<ManagedRealmSet<E>, SetChange<E>>, Versioned by operator.realmReference {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        return operator.add(element)
    }

    override fun clear() {
        operator.clear()
    }

    override fun contains(element: E): Boolean {
        return operator.contains(element)
    }

    override fun remove(element: E): Boolean {
        return operator.remove(element)
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        return operator.removeAll(elements)
    }

    override fun iterator(): MutableIterator<E> {
        return object : MutableIterator<E> {

            private var expectedModCount = operator.modCount // Current modifications in the map
            private var cursor = 0 // The position returned by next()
            private var lastReturned = -1 // The last known returned position

            override fun hasNext(): Boolean {
                checkConcurrentModification()

                return cursor < size
            }

            override fun next(): E {
                checkConcurrentModification()

                val position = cursor
                if (position >= size) {
                    throw IndexOutOfBoundsException("Cannot access index $position when size is $size. Remember to check hasNext() before using next().")
                }
                val next = operator.get(position)
                lastReturned = position
                cursor = position + 1
                return next
            }

            override fun remove() {
                checkConcurrentModification()

                if (size == 0) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }
                if (lastReturned < 0) {
                    throw IllegalStateException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }

                val erased = getterScope {
                    val element = operator.get(lastReturned)
                    operator.remove(element)
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

            private fun checkConcurrentModification() {
                if (operator.modCount != expectedModCount) {
                    throw ConcurrentModificationException("The underlying RealmSet was modified while iterating it.")
                }
            }
        }
    }

    override fun asFlow(keyPaths: List<String>?): Flow<SetChange<E>> {
        val keyPathInfo = keyPaths?.let {
            Validation.isType<RealmObjectSetOperator<*>>(operator, "Keypaths are only supported for sets of objects.")
            Pair(operator.classKey, keyPaths)
        }
        return operator.realmReference.owner.registerObserver(this, keyPathInfo)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmSet(parent, it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmSet(parent, it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(
        keyPaths: RealmKeyPathArrayPointer?,
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return RealmInterop.realm_set_add_notification_callback(nativePointer, keyPaths, callback)
    }

    override fun changeFlow(scope: ProducerScope<SetChange<E>>): ChangeFlow<ManagedRealmSet<E>, SetChange<E>> =
        RealmSetChangeFlow(scope)

    override fun delete() {
        RealmInterop.realm_set_remove_all(nativePointer)
    }

    internal fun isValid(): Boolean {
        return !nativePointer.isReleased() && RealmInterop.realm_set_is_valid(nativePointer)
    }
}

internal fun <E : BaseRealmObject> ManagedRealmSet<E>.query(
    query: String,
    args: Array<out Any?>
): RealmQuery<E> {
    val operator: RealmObjectSetOperator<E> = operator as RealmObjectSetOperator<E>
    val queryPointer = inputScope {
        val queryArgs = convertToQueryArgs(args)
        RealmInterop.realm_query_parse_for_set(
            this@query.nativePointer,
            query,
            queryArgs
        )
    }
    return ObjectBoundQuery(
        parent,
        ObjectQuery(
            operator.realmReference,
            operator.classKey,
            operator.clazz,
            operator.mediator,
            queryPointer,
        )
    )
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface SetOperator<E> : CollectionOperator<E, RealmSetPointer> {

    // Modification counter used to detect concurrent writes from the iterator, taken from Java's
    // AbstractList implementation
    var modCount: Int
    override val nativePointer: RealmSetPointer

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        realmReference.checkClosed()
        return addInternal(element, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        realmReference.checkClosed()
        return addAllInternal(elements, updatePolicy, cache)
            .also { modCount++ }
    }

    fun addAllInternal(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: UnmanagedToManagedObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = addInternal(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun clear() {
        realmReference.checkClosed()
        RealmInterop.realm_set_clear(nativePointer)
        modCount++
    }

    fun remove(element: E): Boolean {
        return inputScope {
            with(valueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_erase(nativePointer, transport)
            }
        }.also { modCount++ }
    }

    fun removeAll(elements: Collection<E>): Boolean {
        return elements.fold(false) { accumulator, value ->
            remove(value) or accumulator
        }
    }

    fun get(index: Int): E
    fun contains(element: E): Boolean
    fun copy(realmReference: RealmReference, nativePointer: RealmSetPointer): SetOperator<E>
}

internal class TypeAdaptedSetOperator<E, S>(
    private val setOperator: SetOperator<S>,
    private val typeAdapter: RealmTypeAdapter<S, E>,
) : SetOperator<E> {
    override var modCount: Int by setOperator::modCount
    override val nativePointer: RealmSetPointer by setOperator::nativePointer

    override val mediator: Mediator by setOperator::mediator
    override val realmReference: RealmReference by setOperator::realmReference
    override val valueConverter: RealmValueConverter<E> by lazy { throw RuntimeException("TypeAdaptedSetOperator does not have a valueConverter") }

    override fun get(index: Int): E = typeAdapter.fromRealm(setOperator.get(index))

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer,
    ): SetOperator<E> = TypeAdaptedSetOperator(setOperator.copy(realmReference, nativePointer), typeAdapter)

    override fun contains(element: E): Boolean = setOperator.contains(typeAdapter.toRealm(element))

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache,
    ): Boolean = setOperator.addInternal(typeAdapter.toRealm(element), updatePolicy, cache)
}

internal class PrimitiveSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<E>,
    override val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override var modCount: Int = 0

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(valueConverter) {
                val transport = realm_set_get(nativePointer, index.toLong())
                realmValueToPublic(transport)
            } as E
        }
    }

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            with(valueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_insert(nativePointer, transport)
            }
        }
    }

    override fun contains(element: E): Boolean {
        return inputScope {
            with(valueConverter) {
                val transport = publicToRealmValue(element)
                RealmInterop.realm_set_find(nativePointer, transport)
            }
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperator(mediator, realmReference, valueConverter, nativePointer)
}

internal class RealmObjectSetOperator<E> constructor(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val valueConverter: RealmValueConverter<E>,
    override val nativePointer: RealmSetPointer,
    val clazz: KClass<E & Any>,
    val classKey: ClassKey
) : SetOperator<E> {

    override var modCount: Int = 0

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: UnmanagedToManagedObjectCache
    ): Boolean {
        return inputScope {
            val objRef = realmObjectToRealmReferenceWithImport(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            RealmInterop.realm_set_insert(nativePointer, transport)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(valueConverter) {
                realm_set_get(nativePointer, index.toLong())
                    .let { transport ->
                        when (ValueType.RLM_TYPE_NULL) {
                            transport.getType() -> null
                            else -> realmValueToPublic(transport)
                        }
                    } as E
            }
        }
    }

    override fun contains(element: E): Boolean {
        return inputScope {
            val objRef = realmObjectToRealmReferenceWithImport(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                UpdatePolicy.ALL,
                mutableMapOf()
            )
            val transport = realmObjectTransport(objRef as RealmObjectInterop)
            RealmInterop.realm_set_find(nativePointer, transport)
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> {
        val converter =
            converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectSetOperator(mediator, realmReference, converter, nativePointer, clazz, classKey)
    }
}

internal class RealmSetChangeFlow<E>(scope: ProducerScope<SetChange<E>>) :
    ChangeFlow<ManagedRealmSet<E>, SetChange<E>>(scope) {
    override fun initial(frozenRef: ManagedRealmSet<E>): SetChange<E> = InitialSetImpl(frozenRef)

    override fun update(frozenRef: ManagedRealmSet<E>, change: RealmChangesPointer): SetChange<E>? {
        val builder = SetChangeSetBuilderImpl(change)
        return UpdatedSetImpl(frozenRef, builder.build())
    }

    override fun delete(): SetChange<E> = DeletedSetImpl(UnmanagedRealmSet())
}

internal fun <T> Array<out T>.asRealmSet(): RealmSet<T> =
    UnmanagedRealmSet<T>().apply { addAll(this@asRealmSet) }
