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
    delegate: RealmListDelegate<E>
) : RealmListDelegate<E> by delegate {

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
internal interface RealmListDelegate<E> : MutableList<E>, MutableCollection<E>

/**
 * TODO
 */
private class UnmanagedListDelegate<E> : RealmListDelegate<E> {

    private val unmanagedList = mutableListOf<E>()

    override val size: Int
        get() = unmanagedList.size

    override fun contains(element: E): Boolean = unmanagedList.contains(element)

    override fun containsAll(elements: Collection<E>): Boolean =
        unmanagedList.containsAll(elements)

    override fun get(index: Int): E = unmanagedList[index]

    override fun indexOf(element: E): Int = unmanagedList.indexOf(element)

    override fun isEmpty(): Boolean = unmanagedList.isEmpty()

    override fun iterator(): MutableIterator<E> = unmanagedList.iterator()

    override fun lastIndexOf(element: E): Int = unmanagedList.lastIndexOf(element)

    override fun add(element: E): Boolean = unmanagedList.add(element)

    override fun add(index: Int, element: E) = unmanagedList.add(index, element)

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        unmanagedList.addAll(index, elements)

    override fun addAll(elements: Collection<E>): Boolean = unmanagedList.addAll(elements)

    override fun clear() = unmanagedList.clear()

    override fun listIterator(): MutableListIterator<E> = unmanagedList.listIterator()

    override fun listIterator(index: Int): MutableListIterator<E> =
        unmanagedList.listIterator(index)

    override fun remove(element: E): Boolean = unmanagedList.remove(element)

    override fun removeAll(elements: Collection<E>): Boolean = unmanagedList.removeAll(elements)

    override fun removeAt(index: Int): E = unmanagedList.removeAt(index)

    override fun retainAll(elements: Collection<E>): Boolean = unmanagedList.retainAll(elements)

    override fun set(index: Int, element: E): E = unmanagedList.set(index, element)

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
        unmanagedList.subList(fromIndex, toIndex)
}

/**
 * TODO
 */
private class ManagedListDelegate<E>(
    private val listPtr: NativePointer,
    metadata: RealmList.OperatorMetadata
) : RealmListDelegate<E> {

    private val operator = RealmList.Operator<E>(metadata)

    override val size: Int
        get() = RealmInterop.realm_list_size(listPtr).toInt()

    override fun contains(element: E): Boolean {
        TODO("Not yet implemented")
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun get(index: Int): E =
        operator.convert(RealmInterop.realm_list_get(listPtr, index.toLong()))

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

    override fun add(element: E): Boolean = RealmInterop.realm_list_add(listPtr, element)
        .let { true }

    override fun add(index: Int, element: E) =
        RealmInterop.realm_list_add(listPtr, index.toLong(), element)

    override fun addAll(index: Int, elements: Collection<E>): Boolean =
        size.let { sizeBefore ->
            // TODO could be optimized if the C API had a method for bulk inserting collections
            for ((i, e) in elements.withIndex()) {
                add(index + i, e)
            }
            sizeBefore != size
        }

    override fun addAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

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

    override fun removeAt(index: Int): E {
        TODO("Not yet implemented")
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        TODO("Not yet implemented")
    }

    override fun set(index: Int, element: E): E {
        TODO("Not yet implemented")
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        TODO("Not yet implemented")
    }
}

// --------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------
// --------------------------------------------------------------------------------------------

///**
// * TODO
// */
//class RealmList<E> : MutableList<E>, AbstractMutableCollection<E> {
//
//    private var facade: ListFacade<E>
//
//    // -------------------------------------------------
//    // Unmanaged
//    // -------------------------------------------------
//
//    /**
//     * Constructs a `RealmList` in unmanaged mode.
//     */
//    constructor() : super() {
//        this.facade = UnmanagedListFacade()
//    }
//
//    // -------------------------------------------------
//    // Managed
//    // -------------------------------------------------
//
//    /**
//     * Constructs a `RealmList` in managed mode. This constructor is used internally by Realm.
//     */
//    constructor(
//        listPtr: NativePointer,
//        metadata: OperatorMetadata
//    ) : super() {
//        this.facade = ManagedListFacade(listPtr, Operator(metadata))
//    }
//
//    override val size: Int
//        get() = facade.size
//
//    override fun get(index: Int): E = facade[index]
//
//    override fun indexOf(element: E): Int = facade.indexOf(element)
//
//    override fun iterator(): MutableIterator<E> = facade.iterator()
//
//    override fun lastIndexOf(element: E): Int = facade.lastIndexOf(element)
//
//    override fun add(element: E): Boolean = facade.add(element)
//
//    override fun add(index: Int, element: E) = facade.add(index, element)
//
//    override fun addAll(index: Int, elements: Collection<E>): Boolean =
//        facade.addAll(index, elements)
//
//    override fun listIterator(): MutableListIterator<E> = facade.listIterator()
//
//    override fun listIterator(index: Int): MutableListIterator<E> = facade.listIterator(index)
//
//    override fun removeAt(index: Int): E = facade.removeAt(index)
//
//    override fun set(index: Int, element: E): E = facade.set(index, element)
//
//    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
//        facade.subList(fromIndex, toIndex)
//
//    override fun clear() = facade.clear()
//
//    /**
//     * TODO
//     */
//    data class OperatorMetadata(
//        val clazz: KClass<*>,
//        val isRealmObject: Boolean,
//        val mediator: Mediator,
//        val realmPointer: NativePointer
//    )
//
//    /**
//     * TODO
//     */
//    internal inner class Operator(
//        private val metadata: OperatorMetadata
//    ) {
//
//        @Suppress("UNCHECKED_CAST")
//        fun convert(value: Any?): E {
//            if (value == null) {
//                return null as E
//            }
//            return when (metadata.clazz) {
//                Byte::class -> (value as Long).toByte()
//                Char::class -> (value as Long).toChar()
//                Short::class -> (value as Long).toShort()
//                Int::class -> (value as Long).toInt()
//                else -> with(metadata) {
//                    if (isRealmObject) {
//                        mediator.createInstanceOf(clazz).link(
//                            realmPointer,
//                            mediator,
//                            clazz as KClass<out RealmObject>,
//                            value as Link
//                        )
//                    } else {
//                        value
//                    }
//                }
//            } as E
//        }
//    }
//}
//
///**
// * TODO
// */
//private abstract class ListFacade<E> : MutableList<E>, AbstractMutableCollection<E>()
//
///**
// * TODO
// */
//private class UnmanagedListFacade<E> : ListFacade<E>() {
//
//    private val unmanagedList = mutableListOf<E>()
//
//    override val size: Int
//        get() = unmanagedList.size
//
//    override fun get(index: Int): E = unmanagedList[index]
//
//    override fun indexOf(element: E): Int = unmanagedList.indexOf(element)
//
//    override fun iterator(): MutableIterator<E> = unmanagedList.iterator()
//
//    override fun lastIndexOf(element: E): Int = unmanagedList.lastIndexOf(element)
//
//    override fun add(element: E): Boolean = unmanagedList.add(element)
//
//    override fun add(index: Int, element: E) = unmanagedList.add(index, element)
//
//    override fun addAll(index: Int, elements: Collection<E>): Boolean =
//        unmanagedList.addAll(index, elements)
//
//    override fun listIterator(): MutableListIterator<E> = unmanagedList.listIterator()
//
//    override fun listIterator(index: Int): MutableListIterator<E> =
//        unmanagedList.listIterator(index)
//
//    override fun removeAt(index: Int): E = unmanagedList.removeAt(index)
//
//    override fun set(index: Int, element: E): E = unmanagedList.set(index, element)
//
//    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> =
//        unmanagedList.subList(fromIndex, toIndex)
//}
//
///**
// * TODO
// */
//private class ManagedListFacade<E>(
//    private val listPtr: NativePointer,
//    private val operator: RealmList<E>.Operator
//) : ListFacade<E>() {
//
//    override val size: Int
//        get() = RealmInterop.realm_list_size(listPtr).toInt()
//
//    override fun get(index: Int): E =
//        operator.convert(RealmInterop.realm_list_get(listPtr, index.toLong()))
//
//    override fun indexOf(element: E): Int {
//        TODO("indexOf(element: E) - Not yet implemented")
//    }
//
//    override fun iterator(): MutableIterator<E> {
//        TODO("iterator() - Not yet implemented")
//    }
//
//    override fun lastIndexOf(element: E): Int {
//        TODO("lastIndexOf(element: E) - Not yet implemented")
//    }
//
//    override fun add(element: E): Boolean = RealmInterop.realm_list_add(listPtr, element)
//        .let { true }
//
//    override fun add(index: Int, element: E) =
//        RealmInterop.realm_list_add(listPtr, index.toLong(), element)
//
//    override fun addAll(index: Int, elements: Collection<E>): Boolean =
//        size.let { sizeBefore ->
//            // TODO could be optimized if the C API had a method for bulk inserting collections
//            for ((i, e) in elements.withIndex()) {
//                add(index + i, e)
//            }
//            sizeBefore != size
//        }
//
//    override fun listIterator(): MutableListIterator<E> {
//        TODO("listIterator() - Not yet implemented")
//    }
//
//    override fun listIterator(index: Int): MutableListIterator<E> {
//        TODO("listIterator(index: Int) - Not yet implemented")
//    }
//
//    override fun removeAt(index: Int): E {
//        TODO("removeAt(index: Int) - Not yet implemented")
//    }
//
//    override fun set(index: Int, element: E): E {
//        TODO("set(index: Int, element: E) - Not yet implemented")
//    }
//
//    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
//        TODO("subList(fromIndex: Int, toIndex: Int) - Not yet implemented")
//    }
//
//    override fun clear() = RealmInterop.realm_list_clear(listPtr)
//}
