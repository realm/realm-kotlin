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

import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.internal.interop.ArrayAccessor
import io.realm.internal.interop.Callback
import io.realm.internal.interop.CollectionChangeSetBuilder
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
import io.realm.notifications.DeletedListImpl
import io.realm.notifications.InitialListImpl
import io.realm.notifications.ListChange
import io.realm.notifications.UpdatedList
import io.realm.notifications.UpdatedListImpl
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedRealmList<E> : RealmList<E>, MutableList<E> by mutableListOf() {
    override fun asFlow(): Flow<ListChange<RealmList<E>>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    private val nativePointer: NativePointer,
    private val metadata: ListOperatorMetadata
) : AbstractMutableList<E>(), RealmList<E>, Observable<ManagedRealmList<E>, ListChange<RealmList<E>>>, Flowable<ListChange<RealmList<E>>> {

    private val operator = ListOperator<E>(metadata)

    override val size: Int
        get() {
            metadata.realm.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        metadata.realm.checkClosed()
        try {
            return operator.convert(RealmInterop.realm_list_get(nativePointer, index.toLong()))
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not get element at list index $index",
                exception
            )
        }
    }

    override fun add(index: Int, element: E) {
        metadata.realm.checkClosed()
        try {
            RealmInterop.realm_list_add(
                nativePointer,
                index.toLong(),
                copyToRealm(metadata.mediator, metadata.realm, element)
            )
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not add element at list index $index",
                exception
            )
        }
    }

    override fun clear() {
        metadata.realm.checkClosed()
        RealmInterop.realm_list_clear(nativePointer)
    }

    override fun removeAt(index: Int): E = get(index).also {
        metadata.realm.checkClosed()
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
        metadata.realm.checkClosed()
        try {
            return operator.convert(
                RealmInterop.realm_list_set(
                    nativePointer,
                    index.toLong(),
                    copyToRealm(metadata.mediator, metadata.realm, element)
                )
            )
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not set list element at list index $index",
                exception
            )
        }
    }

    override fun asFlow(): Flow<ListChange<RealmList<E>>> {
        metadata.realm.checkClosed()
        return metadata.realm.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmList(it, metadata.copy(realm = frozenRealm))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmList<E>? {
        return RealmInterop.realm_list_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmList(it, metadata.copy(realm = liveRealm))
        }
    }

    override fun registerForNotification(callback: Callback): NativePointer {
        return RealmInterop.realm_list_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: NativePointer,
        channel: SendChannel<ListChange<RealmList<E>>>
    ): ChannelResult<Unit>? {
        val frozenList: ManagedRealmList<E>? = freeze(frozenRealm)
        return if (frozenList != null) {
            val builder = UpdatedListBuilder(frozenList)
            RealmInterop.realm_collection_changes_get_changes(change, builder)
            RealmInterop.realm_collection_changes_get_ranges(change, builder)

            if (builder.isEmpty()) {
                channel.trySend(InitialListImpl(frozenList))
            } else {
                channel.trySend(builder.build())
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
}

internal class UpdatedListBuilder<T : List<*>>(val list: T) :
    CollectionChangeSetBuilder<UpdatedList<T>, ListChange.Range>() {

    override fun initIndicesArray(size: Int, indicesAccessor: ArrayAccessor): IntArray =
        IntArray(size) { index -> indicesAccessor(index) }

    override fun initRangesArray(
        size: Int,
        fromAccessor: ArrayAccessor,
        toAccessor: ArrayAccessor
    ): Array<ListChange.Range> =
        Array(size) { index ->
            val from: Int = fromAccessor(index)
            val to: Int = toAccessor(index)
            ListChange.Range(from, to - from)
        }

    override fun build(): UpdatedList<T> = UpdatedListImpl(
        list = list,
        deletions = deletionIndices,
        insertions = insertionIndices,
        changes = modificationIndicesAfter,
        deletionRanges = deletionRanges,
        insertionRanges = insertionRanges,
        changeRanges = modificationRangesAfter
    )
}

/**
 * Metadata needed to correctly instantiate a list operator.
 */
internal data class ListOperatorMetadata(
    val clazz: KClass<*>,
    val mediator: Mediator,
    val realm: RealmReference
)

/**
 * Facilitates conversion between Realm Core types and Kotlin types.
 */
internal class ListOperator<E>(
    private val metadata: ListOperatorMetadata
) {

    /**
     * Converts the underlying Core type to the correct type expressed in the RealmList.
     */
    @Suppress("UNCHECKED_CAST")
    fun convert(value: Any?): E {
        if (value == null) {
            return null as E
        }
        return with(metadata) {
            when (clazz) {
                Byte::class -> (value as Long).toByte()
                Char::class -> (value as Long).toInt().toChar()
                Short::class -> (value as Long).toShort()
                Int::class -> (value as Long).toInt()
                Long::class,
                Boolean::class,
                Float::class,
                Double::class,
                String::class -> value
                RealmInstant::class -> RealmInstantImpl(value as Timestamp)
                else -> (value as Link).toRealmObject(
                    clazz as KClass<out RealmObject>,
                    mediator,
                    realm
                )
            } as E
        }
    }
}

/**
 * Instantiates a [RealmList] in **managed** mode.
 */
internal fun <T> managedRealmList(
    listPointer: NativePointer,
    metadata: ListOperatorMetadata
): ManagedRealmList<T> = ManagedRealmList(listPointer, metadata)

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
