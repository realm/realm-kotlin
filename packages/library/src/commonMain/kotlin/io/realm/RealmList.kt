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
import io.realm.internal.link
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

/**
 * TODO
 */
class RealmList<E> private constructor(
    delegate: MutableList<E>
) : MutableList<E> by delegate {

    /**
     * Constructs a `RealmList` in unmanaged mode.
     */
    constructor() : this(UnmanagedListDelegate())

    /**
     * Constructs a `RealmList` in managed mode. This constructor is used internally by Realm.
     */
    constructor(
        listPtr: NativePointer,
        metadata: OperatorMetadata
    ) : this(ManagedListDelegate(listPtr, metadata))

    /**
     * TODO
     */
    data class OperatorMetadata(
        val clazz: KClass<*>,
        val isRealmObject: Boolean,
        val mediator: Mediator,
        val realmPointer: NativePointer
    )

    /**
     * TODO
     */
    internal class Operator<E>(
        private val metadata: OperatorMetadata
    ) {

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
                        isRealmObject -> mediator.createInstanceOf(clazz).link(
                            realmPointer,
                            mediator,
                            clazz as KClass<out RealmObject>,
                            value as Link
                        )
                        else -> value
                    }
                } as E
            }
        }
    }

}

/**
 * TODO
 */
private class UnmanagedListDelegate<E>(
    list: MutableList<E> = mutableListOf()
) : MutableList<E> by list

/**
 * TODO
 */
private class ManagedListDelegate<E>(
    private val listPtr: NativePointer,
    metadata: RealmList.OperatorMetadata
) : MutableList<E> {

    private val operator = RealmList.Operator<E>(metadata)

    override val size: Int
        get() = RealmInterop.realm_list_size(listPtr).toInt()

    override fun contains(element: E): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): E {
        if (index < 0 || index > size - 1) {
            throw IndexOutOfBoundsException("Index: '$index', size: '$size'")
        }
        return operator.convert(RealmInterop.realm_list_get(listPtr, index.toLong()))
    }

    override fun indexOf(element: E): Int {
        TODO("Not yet implemented")
    }

    override fun isEmpty(): Boolean = size == 0

    override fun iterator(): MutableIterator<E> {
        TODO("Not yet implemented")
    }

    override fun lastIndexOf(element: E): Int {
        TODO("Not yet implemented")
    }

    override fun add(element: E): Boolean = add(size, element).let { true }

    override fun add(index: Int, element: E) {
        if (index < 0 || index > size) {
            throw IndexOutOfBoundsException("Index: '$index', size: '$size'")
        }
        RealmInterop.realm_list_add(listPtr, index.toLong(), element)
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        val sizeBefore = size
        if (index < 0 || index > sizeBefore) {
            throw IndexOutOfBoundsException("Index was '$index' but size was '$sizeBefore'")
        }

        var modified = false
        for ((i, e) in elements.withIndex()) {
            add(index + i, e)
            modified = true
        }
        return modified
    }

    override fun addAll(elements: Collection<E>): Boolean = addAll(size, elements)

    override fun clear() = RealmInterop.realm_list_clear(listPtr)

    override fun listIterator(): MutableListIterator<E> {
        TODO("Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        TODO("Not yet implemented")
    }

    override fun remove(element: E): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAt(index: Int): E = get(index).also {
        if (size == 0 || index < 0 || index > size - 1) {
            throw IndexOutOfBoundsException("Index: '$index', size: '$size'")
        }
        RealmInterop.realm_list_erase(listPtr, index.toLong())
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun set(index: Int, element: E): E {
        if (size == 0 || index < 0 || index > size - 1) {
            throw IndexOutOfBoundsException("Index: '$index', size: '$size'")
        }
        return operator.convert(RealmInterop.realm_list_set(listPtr, index.toLong(), element))
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        TODO("Not yet implemented")
    }
}
