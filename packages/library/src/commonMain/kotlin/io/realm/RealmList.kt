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

import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop

/**
 * TODO
 */
class RealmList<E> : MutableList<E>, AbstractMutableCollection<E> {

    private var facade: ListFacade<E>

    // -------------------------------------------------
    // Unmanaged
    // -------------------------------------------------

    /**
     * Constructs a `RealmList` in unmanaged mode.
     */
    constructor() : super() {
        this.facade = UnmanagedListFacade()
    }

    // -------------------------------------------------
    // Managed
    // -------------------------------------------------

    /**
     * Constructs a `RealmList` in managed mode. This constructor is used internally by Realm.
     */
    constructor(listPtr: NativePointer) : super() {
        this.facade = ManagedListFacade(listPtr)
    }

    override val size: Int
        get() = facade.size

    override fun get(index: Int): E = facade[index]

    override fun indexOf(element: E): Int = facade.indexOf(element)

    override fun iterator(): MutableIterator<E> = facade.iterator()

    override fun lastIndexOf(element: E): Int = facade.lastIndexOf(element)

    override fun add(element: E): Boolean = facade.add(element)

    override fun add(index: Int, element: E) = facade.add(index, element)

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        facade.addAll(index, elements)

    override fun listIterator(): MutableListIterator<E> = facade.listIterator()

    override fun listIterator(index: Int): MutableListIterator<E> = facade.listIterator(index)

    override fun removeAt(index: Int): E = facade.removeAt(index)

    override fun set(index: Int, element: E): E = facade.set(index, element)

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
        facade.subList(fromIndex, toIndex)

    override fun clear() = facade.clear()
}

/**
 * TODO
 */
private abstract class ListFacade<E> : MutableList<E>, AbstractMutableCollection<E>()

/**
 * TODO
 */
private class UnmanagedListFacade<E> : ListFacade<E>() {

    private val unmanagedList = mutableListOf<E>()

    override val size: Int
        get() = unmanagedList.size

    override fun get(index: Int): E = unmanagedList[index]

    override fun indexOf(element: E): Int = unmanagedList.indexOf(element)

    override fun iterator(): MutableIterator<E> = unmanagedList.iterator()

    override fun lastIndexOf(element: E): Int = unmanagedList.lastIndexOf(element)

    override fun add(element: E): Boolean = unmanagedList.add(element)

    override fun add(index: Int, element: E) = unmanagedList.add(index, element)

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        unmanagedList.addAll(index, elements)

    override fun listIterator(): MutableListIterator<E> = unmanagedList.listIterator()

    override fun listIterator(index: Int): MutableListIterator<E> =
        unmanagedList.listIterator(index)

    override fun removeAt(index: Int): E = unmanagedList.removeAt(index)

    override fun set(index: Int, element: E): E = unmanagedList.set(index, element)

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
        unmanagedList.subList(fromIndex, toIndex)
}

/**
 * TODO
 */
private class ManagedListFacade<E>(
    private val listPtr: NativePointer
) : ListFacade<E>() {

    override val size: Int
        get() = RealmInterop.realm_list_size(listPtr).toInt()

    override fun get(index: Int): E = RealmInterop.realm_list_get(listPtr, index.toLong())

    override fun indexOf(element: E): Int {
        TODO("indexOf(element: E) - Not yet implemented")
    }

    override fun iterator(): MutableIterator<E> {
        TODO("iterator() - Not yet implemented")
    }

    override fun lastIndexOf(element: E): Int {
        TODO("lastIndexOf(element: E) - Not yet implemented")
    }

    override fun add(element: E): Boolean = RealmInterop.realm_list_add(listPtr, element)
        .let { true }

    override fun add(index: Int, element: E) =
        RealmInterop.realm_list_add(listPtr, index.toLong(), element)

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        TODO("addAll(index: Int, elements: Collection<E>) - Not yet implemented")
    }

    override fun listIterator(): MutableListIterator<E> {
        TODO("listIterator() - Not yet implemented")
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        TODO("listIterator(index: Int) - Not yet implemented")
    }

    override fun removeAt(index: Int): E {
        TODO("removeAt(index: Int) - Not yet implemented")
    }

    override fun set(index: Int, element: E): E {
        TODO("set(index: Int, element: E) - Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        TODO("subList(fromIndex: Int, toIndex: Int) - Not yet implemented")
    }

    override fun clear() = RealmInterop.realm_list_clear(listPtr)
}
