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
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.MemAllocator
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_erase
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_find
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_get
import io.realm.kotlin.internal.interop.RealmInterop.realm_set_insert
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.interop.setterScope
import io.realm.kotlin.internal.interop.setterScopeTracked
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.internal.DeletedSetImpl
import io.realm.kotlin.notifications.internal.InitialSetImpl
import io.realm.kotlin.notifications.internal.UpdatedSetImpl
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged sets, backed by a [MutableSet].
 */
internal class UnmanagedRealmSet<E> : RealmSet<E>, InternalDeleteable, MutableSet<E> by mutableSetOf() {
    override fun asFlow(): Flow<SetChange<E>> {
        throw UnsupportedOperationException("Unmanaged sets cannot be observed.")
    }

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }
}

/**
 * Implementation for managed sets, backed by Realm.
 */
internal class ManagedRealmSet<E>(
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E>, InternalDeleteable, Observable<ManagedRealmSet<E>, SetChange<E>>, Flowable<SetChange<E>> {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        operator.realmReference.checkClosed()
        try {
            return operator.add(element)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not add element to set"
            )
        }
    }

    override fun clear() {
        operator.realmReference.checkClosed()
        RealmInterop.realm_set_clear(nativePointer)
    }

    override fun contains(element: E): Boolean {
        operator.realmReference.checkClosed()
        return operator.contains(element)
    }

    override fun iterator(): MutableIterator<E> {
        operator.realmReference.checkClosed()
        return object : MutableIterator<E> {

            private var pos = -1

            override fun hasNext(): Boolean = pos + 1 < size

            override fun next(): E {
                pos = pos.inc()
                if (pos >= size) {
                    throw NoSuchElementException("Cannot access index $pos when size is $size. Remember to check hasNext() before using next().")
                }
                return operator.get(pos)
            }

            override fun remove() {
                if (pos < 0) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }
                if (isEmpty()) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }

                val erased = getterScope {
                    val transport = realm_set_get(nativePointer, pos.toLong())
                    transport.realm_set_erase(nativePointer)
                }
                if (!erased) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
                }
            }
        }
    }

    override fun asFlow(): Flow<SetChange<E>> {
        operator.realmReference.checkClosed()
        return operator.realmReference.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmSet(it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmSet(it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return RealmInterop.realm_set_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<SetChange<E>>
    ): ChannelResult<Unit>? {
        val frozenSet: ManagedRealmSet<E>? = freeze(frozenRealm)
        return if (frozenSet != null) {
            val builder = SetChangeSetBuilderImpl(change)

            if (builder.isEmpty()) {
                channel.trySend(InitialSetImpl(frozenSet))
            } else {
                channel.trySend(UpdatedSetImpl(frozenSet, builder.build()))
            }
        } else {
            channel.trySend(DeletedSetImpl(UnmanagedRealmSet()))
                .also {
                    channel.close()
                }
        }
    }

    override fun delete() {
        RealmInterop.realm_set_remove_all(nativePointer)
    }
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface SetOperator<E> : CollectionOperator<E> {

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = add(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun get(index: Int): E
    fun contains(element: E): Boolean
    fun copy(realmReference: RealmReference, nativePointer: RealmSetPointer): SetOperator<E>
}

/**
 * Base class for operator for primitive types. Children can be 'tracked' (i.e. the underlying C
 * struct contains pointers to some buffers that require cleanup, e.g. strings or byte arrays) or
 * 'untracked' (i.e. all other primitive types).
 */
internal abstract class PrimitiveSetOperator<E> constructor(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    protected val nativePointer: RealmSetPointer
) : SetOperator<E> {

    abstract fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache,
        block: MemAllocator.() -> Boolean
    ): Boolean

    abstract fun containsInternal(element: E, block: MemAllocator.() -> Boolean): Boolean

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(converter) {
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

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        return addInternal(element, updatePolicy, cache) {
            with(converter) {
                val transport = publicToRealmValue(element)
                transport.realm_set_insert(nativePointer)
            }
        }
    }

    override fun contains(element: E): Boolean {
        return containsInternal(element) {
            with(converter) {
                val transport = publicToRealmValue(element)
                transport.realm_set_find(nativePointer)
            }
        }
    }
}

/**
 * Operator for strings and byte arrays. Calls to [setterScopeTracked] ensure data buffers are
 * cleaned after completion.
 */
internal class PrimitiveSetOperatorTracked<E> constructor(
    mediator: Mediator,
    realmReference: RealmReference,
    converter: RealmValueConverter<E>,
    nativePointer: RealmSetPointer
) : PrimitiveSetOperator<E>(mediator, realmReference, converter, nativePointer) {

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache,
        block: MemAllocator.() -> Boolean
    ): Boolean {
        return setterScopeTracked(block)
    }

    override fun containsInternal(element: E, block: MemAllocator.() -> Boolean): Boolean {
        return setterScopeTracked(block)
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperatorTracked(mediator, realmReference, converter, nativePointer)
}

/**
 * Operator for Realm primitive types other than strings and byte arrays.
 */
internal class PrimitiveSetOperatorUntracked<E> constructor(
    mediator: Mediator,
    realmReference: RealmReference,
    converter: RealmValueConverter<E>,
    nativePointer: RealmSetPointer
) : PrimitiveSetOperator<E>(mediator, realmReference, converter, nativePointer) {

    override fun addInternal(
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache,
        block: MemAllocator.() -> Boolean
    ): Boolean {
        return setterScope(block)
    }

    override fun containsInternal(element: E, block: MemAllocator.() -> Boolean): Boolean {
        return setterScope(block)
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperatorUntracked(mediator, realmReference, converter, nativePointer)
}

/**
 * Operator for Realm objects.
 */
internal class RealmObjectSetOperator<E>(
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val nativePointer: RealmSetPointer,
    private val clazz: KClass<*>,
) : SetOperator<E> {

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean {
        return setterScope {
            val objRef = realmObjectToRef(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            val transport = transportOf(objRef as RealmObjectInterop)
            transport.realm_set_insert(nativePointer)
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E {
        return getterScope {
            with(converter) {
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
        return setterScope {
            val objRef = realmObjectToRef(
                element as BaseRealmObject?,
                mediator,
                realmReference,
                UpdatePolicy.ALL,
                mutableMapOf()
            )
            val transport = transportOf(objRef as RealmObjectInterop)
            transport.realm_set_find(nativePointer)
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> {
        val converter =
            converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectSetOperator(mediator, realmReference, converter, nativePointer, clazz)
    }
}

internal fun <T> Array<out T>.asRealmSet(): RealmSet<T> =
    UnmanagedRealmSet<T>().apply { addAll(this@asRealmSet) }
