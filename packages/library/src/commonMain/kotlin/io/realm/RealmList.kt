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

package io.realm

import io.realm.internal.Freezable
import io.realm.internal.Mediator
import io.realm.internal.NativeComponent
import io.realm.internal.RealmReference
import io.realm.internal.copyToRealm
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * RealmList is used to model one-to-many relationships in a [RealmObject].
 *
 * A RealmList has two modes: `managed` and `unmanaged`. In `managed` mode all objects are persisted
 * inside a Realm whereas in `unmanaged` mode it works as a normal [MutableList].
 *
 * Only Realm can create managed RealmLists. Managed RealmLists will automatically update their
 * content whenever the underlying Realm is updated. Said content can only be accessed using the
 * getter of a [RealmObject].
 *
 * Unmanaged RealmLists can be created by the user and can contain both managed and unmanaged
 * [RealmObject]s. This is useful when dealing with JSON deserializers like Gson or other frameworks
 * that inject values into a class. Unmanaged elements in a list can be added to a Realm using the
 * [MutableRealm.copyToRealm] method.
 */
interface RealmList<E> : MutableList<E>, Observable<RealmList<E>>

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
    override val nativePointer: NativePointer,
    val metadata: ListOperatorMetadata
) : RealmList<E>, Freezable<RealmList<E>>, NativeComponent, AbstractMutableList<E>() {

    private val operator = ListOperator<E>(metadata)

    override val size: Int
        get() {
            metadata.realm.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        metadata.realm.checkClosed()
        return operator.convert(RealmInterop.realm_list_get(nativePointer, index.toLong()))
    }

    override fun add(index: Int, element: E) {
        metadata.realm.checkClosed()
        RealmInterop.realm_list_add(
            nativePointer,
            index.toLong(),
            copyToRealm(metadata.mediator, metadata.realm, element)
        )
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
        RealmInterop.realm_list_erase(nativePointer, index.toLong())
    }

    override fun set(index: Int, element: E): E {
        metadata.realm.checkClosed()
        return operator.convert(
            RealmInterop.realm_list_set(
                nativePointer,
                index.toLong(),
                copyToRealm(metadata.mediator, metadata.realm, element)
            )
        )
    }

    override fun observe(): Flow<RealmList<E>> {
        metadata.realm.checkClosed()
        return metadata.realm.owner.registerListObserver(this)
    }

    override fun freeze(frozenPointer: NativePointer, frozenRealm: RealmReference): RealmList<E> =
        managedRealmList(frozenPointer, metadata.copy(realm = frozenRealm))

    override fun thaw(livePointer: NativePointer, liveRealm: RealmReference): RealmList<E> =
        managedRealmList(livePointer, metadata.copy(realm = liveRealm))

    // TODO from LifeCycle interface
    @Suppress("TooGenericExceptionCaught")
    internal fun isValid(): Boolean {
        // FIXME workaround until https://github.com/realm/realm-core/issues/4843 is done
        try {
            size
        } catch (e: RuntimeException) {
            if (e.message?.lowercase()?.contains("access to invalidated list object") == true) {
                return false
            }
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

/**
 * Instantiates a [RealmList] in **unmanaged** mode.
 */
fun <T> realmListOf(vararg elements: T): RealmList<T> =
    if (elements.isNotEmpty()) elements.asRealmList() else UnmanagedRealmList()

/**
 * Instantiates a [RealmList] in **unmanaged** mode.
 */
fun <T> realmListOf(): RealmList<T> = UnmanagedRealmList()

private fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this) }
