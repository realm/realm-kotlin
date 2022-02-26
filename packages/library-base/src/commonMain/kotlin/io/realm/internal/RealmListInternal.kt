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
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.interop.Callback
import io.realm.internal.interop.Link
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
import io.realm.internal.platform.realmObjectCompanionOrNull
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
internal class UnmanagedRealmList<E> : RealmList<E>, MutableList<E> by mutableListOf() {
    override fun asFlow(): Flow<ListChange<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    private val nativePointer: NativePointer,
    private val metadata: ListOperatorMetadata<E>
) : AbstractMutableList<E>(), RealmList<E>, Observable<ManagedRealmList<E>, ListChange<E>>, Flowable<ListChange<E>> {

    override val size: Int
        get() {
            metadata.realm.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        metadata.realm.checkClosed()
        try {
            return cinteropObjectToUserObject(RealmInterop.realm_list_get(nativePointer, index.toLong()))
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

    /**
     * Converts the given cinterop object to an object of type E.
    */
    private fun cinteropObjectToUserObject(value: Any?): E {
        return value?.let { metadata.converter.convert(value) } as E
    }

    override fun set(index: Int, element: E): E {
        metadata.realm.checkClosed()
        try {
            return cinteropObjectToUserObject(
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

    override fun asFlow(): Flow<ListChange<E>> {
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
}

/**
 * Interface to convert objects returned from the cinterop layer to a specific type.
 *
 * @param E the type that objects are converted to by [convert].
 */
internal fun interface ElementConverter<E> {
    /**
     * Converts the given value to an object of type E.
     */
    fun convert(value: Any?): E
}

/**
 * Metadata needed to correctly instantiate a list operator.
 */
internal data class ListOperatorMetadata<E>(
    val mediator: Mediator,
    val realm: RealmReference,
    val converter: ElementConverter<E>
)

internal fun <E> converter(mediator: Mediator, realm: RealmReference, clazz: KClass<*>): ElementConverter<E> {
    return if (realmObjectCompanionOrNull(clazz) != null || clazz in setOf(DynamicRealmObject::class, DynamicMutableRealmObject::class)) {
        ElementConverter {
            (it as Link).toRealmObject(
                clazz as KClass<out RealmObject>,
                mediator,
                realm
            ) as E
        }
    } else when (clazz) {
        Byte::class -> ElementConverter { (it as Long).toByte() as E }
        Char::class -> ElementConverter { (it as Long).toInt().toChar() as E }
        Short::class -> ElementConverter { (it as Long).toShort() as E }
        Int::class -> ElementConverter { (it as Long).toInt() as E }
        Long::class,
        Boolean::class,
        Float::class,
        Double::class,
        String::class -> ElementConverter { it as E }
        RealmInstant::class -> ElementConverter { RealmInstantImpl(it as Timestamp) as E }
        else -> throw IllegalArgumentException("Unsupported type for RealmList: $clazz")
    }
}

/**
 * Instantiates a [RealmList] in **managed** mode.
 */
internal fun <T> managedRealmList(
    listPointer: NativePointer,
    metadata: ListOperatorMetadata<T>
): ManagedRealmList<T> = ManagedRealmList(listPointer, metadata)

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
