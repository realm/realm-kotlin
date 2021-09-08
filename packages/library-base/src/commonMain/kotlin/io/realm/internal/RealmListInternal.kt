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

import io.realm.RealmList
import io.realm.RealmObject
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmCoreException
import io.realm.interop.RealmCoreInvalidatedObjectException
import io.realm.interop.RealmInterop
import io.realm.toRealmObject
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedRealmList<E> : RealmList<E>, MutableList<E> by mutableListOf() {
    override fun observe(): Flow<RealmList<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    val nativePointer: NativePointer,
    val metadata: ListOperatorMetadata
) : AbstractMutableList<E>(), RealmList<E> {

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
            throw genericRealmCoreExceptionHandler("Cannot get list element at index $index", exception)
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
            throw genericRealmCoreExceptionHandler("Cannot add list element at index $index", exception)
        }
    }

    // FIXME bug in AbstractMutableList.addAll native implementation:
    //  https://youtrack.jetbrains.com/issue/KT-47211
    //  Remove this method once the native implementation has a check for valid index
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        metadata.realm.checkClosed()
        rangeCheckForAdd(index)
        return super.addAll(index, elements)
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
            throw genericRealmCoreExceptionHandler("Cannot remove list element at index $index", exception)
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
            throw genericRealmCoreExceptionHandler("Cannot set list element at index $index", exception)
        }
    }

    override fun observe(): Flow<RealmList<E>> {
        metadata.realm.checkClosed()
        return metadata.realm.owner.registerListObserver(this)
    }

    fun freeze(listPointer: NativePointer, frozenRealm: RealmReference): RealmList<E> {
        val frozenListPointer = RealmInterop.realm_list_freeze(
            listPointer,
            frozenRealm.dbPointer
        )
        return managedRealmList(frozenListPointer, metadata.copy(realm = frozenRealm))
    }

    // TODO from LifeCycle interface
    @Suppress("TooGenericExceptionCaught")
    internal fun isValid(): Boolean {
        // FIXME workaround until https://github.com/realm/realm-core/issues/4843 is done
        try {
            size
        } catch (e: RealmCoreInvalidatedObjectException) {
            return false
        }
        return true
    }

    private fun rangeCheckForAdd(index: Int) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("Index: '$index', Size: '$size'")
        }
    }
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
                Char::class -> (value as Long).toChar()
                Short::class -> (value as Long).toShort()
                Int::class -> (value as Long).toInt()
                Long::class,
                Boolean::class,
                Float::class,
                Double::class,
                String::class -> value
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
): RealmList<T> = ManagedRealmList(listPointer, metadata)

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
