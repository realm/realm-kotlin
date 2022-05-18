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

import io.realm.BaseRealmObject
import io.realm.EmbeddedObject
import io.realm.MutableRealm
import io.realm.BaseRealmObject
import io.realm.MutableRealm
import io.realm.RealmList
import io.realm.internal.RealmObjectHelper.assign
import io.realm.internal.interop.Callback
import io.realm.internal.interop.RealmChangesPointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmListPointer
import io.realm.internal.interop.RealmNotificationTokenPointer
import io.realm.notifications.ListChange
import io.realm.notifications.internal.DeletedListImpl
import io.realm.notifications.internal.InitialListImpl
import io.realm.notifications.internal.UpdatedListImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedRealmList<E> : RealmList<E>, InternalDeleteable, MutableList<E> by mutableListOf() {
    override fun asFlow(): Flow<ListChange<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged lists cannot be deleted.")
    }
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    internal val nativePointer: RealmListPointer,
    val operator: ListOperatorMetadata<E>,
) : AbstractMutableList<E>(), RealmList<E>, InternalDeleteable, Observable<ManagedRealmList<E>, ListChange<E>>, Flowable<ListChange<E>> {
    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        operator.realmReference.checkClosed()
        try {
            return operator.get(index)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not get element at list index $index",
                exception
            )
        }
    }

    override fun add(index: Int, element: E) {
        try {
            operator.insert(index, element)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not add element at list index $index",
                exception
            )
        }
    }

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(elements: Collection<E>): Boolean {
        return operator.insertAll(size, elements)
    }

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkPositionIndex(index, size)
        return operator.insertAll(index, elements)
    }

    override fun clear() {
        operator.realmReference.checkClosed()
        RealmInterop.realm_list_clear(nativePointer)
    }

    override fun removeAt(index: Int): E = get(index).also {
        operator.realmReference.checkClosed()
        try {
            RealmInterop.realm_list_erase(nativePointer, index.toLong())
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not remove element at list index $index",
                exception
            )
        }
    }

    override fun set(index: Int, element: E): E {
        operator.realmReference.checkClosed()
        try {
            return operator.set(index, element)
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not set list element at list index $index",
                exception
            )
        }
    }

    override fun asFlow(): Flow<ListChange<E>> {
        operator.realmReference.checkClosed()
        return operator.realmReference.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmList(it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmList(it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(callback: Callback<RealmChangesPointer>): RealmNotificationTokenPointer {
        return RealmInterop.realm_list_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<ListChange<E>>
    ): ChannelResult<Unit>? {
        val frozenList: ManagedRealmList<E>? = freeze(frozenRealm)
        return if (frozenList != null) {
            val builder = ListChangeSetBuilderImpl(change)

            if (builder.isEmpty()) {
                channel.trySend(InitialListImpl(frozenList))
            } else {
                channel.trySend(UpdatedListImpl(frozenList, builder.build()))
            }
        } else {
            channel.trySend(DeletedListImpl(UnmanagedRealmList()))
                .also {
                    channel.close()
                }
        }
    }

    // TODO from LifeCycle interface
    internal fun isValid(): Boolean {
        return RealmInterop.realm_list_is_valid(nativePointer)
    }

    override fun delete() {
        return RealmInterop.realm_list_remove_all(nativePointer)
    }
}

// Cloned from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/collections/AbstractList.kt
private fun checkPositionIndex(index: Int, size: Int) {
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

/**
 * Metadata needed to correctly instantiate a list operator.
 */
internal interface ListOperatorMetadata<E> {
    val mediator: Mediator
    val realmReference: RealmReference
    val converter: RealmValueConverter<E>
    fun get(index: Int): E
    // TODO OPTIMIZE We technically don't need update policy and cache for primitie lists but right now RealmObjectHelper.assign doesn't know how to differentiate the calls to the operator
    fun insert(index: Int, element: E, updatePolicy: MutableRealm.UpdatePolicy = MutableRealm.UpdatePolicy.ERROR, cache: ObjectCache = mutableMapOf())
    fun insertAll(index: Int, elements: Collection<E>, updatePolicy: MutableRealm.UpdatePolicy = MutableRealm.UpdatePolicy.ERROR, cache: ObjectCache = mutableMapOf()): Boolean {

        @Suppress("VariableNaming")
        var _index = index
        var changed = false
        for (e in elements) {
            insert(_index++, e, updatePolicy, cache)
            changed = true
        }
        return changed
    }
    fun set(index: Int, element: E, updatePolicy: MutableRealm.UpdatePolicy = MutableRealm.UpdatePolicy.ERROR, cache: ObjectCache = mutableMapOf()): E
    // Create a new operator from an existing one to be able to issue frozen/thawed instances of the list operating on the new version of the list
    fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperatorMetadata<E>
}

internal class PrimitiveListOperator<E>(override val mediator: Mediator, override val realmReference: RealmReference, val nativePointer: RealmListPointer, override val converter: RealmValueConverter<E>) : ListOperatorMetadata<E> {
    override fun get(index: Int): E {
        return RealmInterop.realm_list_get(nativePointer, index.toLong())?.let {
            converter.realmValueToPublic(it) as E
        }
    }

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ) {
        RealmInterop.realm_list_add(
            nativePointer,
            index.toLong(),
            converter.publicToRealmValue(element)
        )
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ): E {
        return RealmInterop.realm_list_set(
            nativePointer,
            index.toLong(),
            converter.publicToRealmValue(element)
        )?.let {
            converter.realmValueToPublic(it) as E
        }
    }

    override fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperatorMetadata<E> {
        return PrimitiveListOperator(mediator, realmReference, nativePointer, converter)
    }
}

internal abstract class BaseRealmObjectListOperator<E>(override val mediator: Mediator, override val realmReference: RealmReference, val nativePointer: RealmListPointer, val clazz: KClass<*>, override val converter: RealmValueConverter<E>) : ListOperatorMetadata<E> {
    override fun get(index: Int): E {
        return RealmInterop.realm_list_get(nativePointer, index.toLong())?.let {
            converter.realmValueToPublic(it) as E
        }
    }
}

internal class RealmObjectListOperator<E>(mediator: Mediator, realmReference: RealmReference, nativePointer: RealmListPointer, clazz: KClass<*>, converter: RealmValueConverter<E>) : BaseRealmObjectListOperator<E>(
    mediator, realmReference, nativePointer, clazz, converter
) {
    override fun insert(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ) {
        RealmInterop.realm_list_add(
            nativePointer,
            index.toLong(),
            realmObjectToRealmValue(element as BaseRealmObject?, mediator, realmReference, updatePolicy, cache)
        )
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ): E {
        return RealmInterop.realm_list_set(
            nativePointer,
            index.toLong(),
            realmObjectToRealmValue(element as BaseRealmObject?, mediator, realmReference, updatePolicy, cache)
        )?.let {
            converter.realmValueToPublic(it) as E
        }
    }
    override fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperatorMetadata<E> {
        // FIXME We need to create a new converter every time unless we propagate mediate/realmReference to all operator calls
        val converter: RealmValueConverter<E> = converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectListOperator(mediator, realmReference, nativePointer, clazz, converter)
    }
}

// FIXME Should be EmbeddedObject but embedded DynamicObjects are not EmbeddedObjects
internal class EmbeddedObjectListOperator<E : BaseRealmObject>(mediator: Mediator, realmReference: RealmReference, nativePointer: RealmListPointer, clazz: KClass<*>, converter: RealmValueConverter<E>) : BaseRealmObjectListOperator<E>(
    mediator, realmReference, nativePointer, clazz, converter
) {
    override fun insert(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ) {
        val embedded = RealmInterop.realm_list_insert_embedded(nativePointer, index.toLong())
        val newObj = embedded.toRealmObject<BaseRealmObject>(
            element::class as KClass<BaseRealmObject>,
            mediator,
            realmReference
        )
        assign(
            newObj,
            element,
            mediator,
            realmReference.asValidLiveRealmReference(),
            MutableRealm.UpdatePolicy.ERROR,
            mutableMapOf()
        )
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: MutableRealm.UpdatePolicy,
        cache: ObjectCache
    ): E {
        // FIXME What to return here when the previous version is deleted?
        val embedded = RealmInterop.realm_list_set_embedded(nativePointer, index.toLong())
        val newEmbeddedObject = converter.realmValueToPublic(embedded) as BaseRealmObject
        assign(newEmbeddedObject, element, mediator, realmReference.asValidLiveRealmReference(), MutableRealm.UpdatePolicy.ERROR, mutableMapOf())
        return newEmbeddedObject as E
    }

    override fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperatorMetadata<E> {
        val converter: RealmValueConverter<E> = converter<E>(clazz, mediator, realmReference) as CompositeConverter<E, *>
        return EmbeddedObjectListOperator(mediator, realmReference, nativePointer, clazz, converter)
    }
}

/**
 * Instantiates a [RealmList] in **managed** mode.
 */
internal fun <T> managedRealmList(
    listPointer: RealmListPointer,
    metadata: ListOperatorMetadata<T>
): ManagedRealmList<T> = ManagedRealmList(listPointer, metadata)

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
