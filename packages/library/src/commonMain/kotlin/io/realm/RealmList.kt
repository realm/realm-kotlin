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

import io.realm.internal.Mediator
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

/**
 * RealmList is used to model one-to-many relationships in a [RealmObject].
 *
 * A RealmList has two modes: managed and unmanaged. In `managed` mode all objects are persisted
 * inside a Realm whereas in `unmanaged` mode it works as a normal [MutableList].
 *
 * Only Realm can create managed RealmLists. Managed RealmLists will automatically update their
 * content whenever the underlying Realm is updated. Said content can only be accessed using the
 * getter of a [RealmObject].
 *
 * Unmanaged RealmLists can be created by the user and can contain both managed and unmanaged
 * [RealmObject]s. This is useful when dealing with JSON deserializers like GSON or other frameworks
 * that inject values into a class. Unmanaged elements in a list can be added to a Realm using the
 * [MutableRealm.copyToRealm] method.
 */
class RealmList<E> private constructor(
    delegate: MutableList<E>
) : MutableList<E> by delegate {

    /**
     * Constructs a RealmList in unmanaged mode.
     */
    constructor() : this(UnmanagedListDelegate())

    /**
     * Constructs a RealmList in managed mode. For internal use only.
     */
    constructor(
        listPtr: NativePointer,
        metadata: OperatorMetadata
    ) : this(ManagedListDelegate(listPtr, metadata))

    /**
     * Metadata needed to correctly instantiate a list operator. For internal use only.
     */
    data class OperatorMetadata(
        val clazz: KClass<*>,
        val isRealmObject: Boolean,
        val mediator: Mediator,
        val realmPointer: NativePointer
    )

    /**
     * Facilitates conversion between Realm Core types and Kotlin types.
     */
    internal class Operator<E>(
        private val metadata: OperatorMetadata
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
                    else -> when {
                        isRealmObject -> (value as Link).toRealmObject(
                            clazz as KClass<out RealmObject>,
                            mediator,
                            realmPointer
                        )
                        else -> value
                    }
                } as E
            }
        }
    }
}

/**
 * Represents an unmanaged [RealmList] backed by a [MutableList] via class delegation.
 */
private class UnmanagedListDelegate<E>(
    list: MutableList<E> = mutableListOf()
) : MutableList<E> by list

/**
 * Represents a managed [RealmList]. Its data will be persisted in Realm.
 *
 * [AbstractMutableList] provides enough default implementations on which we can rely. It can also
 * be used as a delegate since it implements [MutableList].
 */
private class ManagedListDelegate<E>(
    private val listPtr: NativePointer,
    metadata: RealmList.OperatorMetadata
) : AbstractMutableList<E>() {

    private val operator = RealmList.Operator<E>(metadata)

    override val size: Int
        get() = RealmInterop.realm_list_size(listPtr).toInt()

    override fun get(index: Int): E {
        rangeCheck(index)
        return operator.convert(RealmInterop.realm_list_get(listPtr, index.toLong()))
    }

    override fun add(index: Int, element: E) {
        rangeCheckForAdd(index)
        RealmInterop.realm_list_add(listPtr, index.toLong(), element)
    }

    // FIXME bug in AbstractMutableList.addAll native implementation:
    //  https://youtrack.jetbrains.com/issue/KT-47211
    //  Remove this method once the native implementation has a check for valid index
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        rangeCheckForAdd(index)
        return super.addAll(index, elements)
    }

    override fun clear() = RealmInterop.realm_list_clear(listPtr)

    override fun removeAt(index: Int): E = get(index).also {
        rangeCheck(index)
        RealmInterop.realm_list_erase(listPtr, index.toLong())
    }

    override fun set(index: Int, element: E): E {
        rangeCheck(index)
        return operator.convert(RealmInterop.realm_list_set(listPtr, index.toLong(), element))
    }

    private fun rangeCheck(index: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index: '$index', Size: '$size'")
        }
    }

    private fun rangeCheckForAdd(index: Int) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("Index: '$index', Size: '$size'")
        }
    }
}
