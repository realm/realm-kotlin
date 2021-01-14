package io.realm

import io.realm.base.BaseRealmModel

class RealmList<E: BaseRealmModel>(override val size: Int = 0): MutableList<E>, Queryable<E> {

    // Realm specific methods




    // Superclass methods
    override fun get(index: Int): E { TODO() }
    override fun contains(element: E): Boolean {
        TODO()
    }

    override fun containsAll(elements: Collection<E>): Boolean {
        TODO()
    }

    override fun indexOf(element: E): Int {
        TODO()
    }

    override fun isEmpty(): Boolean {
        TODO()
    }

    override fun iterator(): MutableIterator<E> {
        TODO()
    }

    override fun lastIndexOf(element: E): Int {
        TODO()
    }

    override fun add(element: E): Boolean {
        TODO()
    }

    override fun add(index: Int, element: E) {
        TODO()
    }

    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        TODO()
    }

    override fun addAll(elements: Collection<E>): Boolean {
        TODO()
    }

    override fun clear() {
        TODO()
    }

    override fun listIterator(): MutableListIterator<E> {
        TODO()
    }

    override fun listIterator(index: Int): MutableListIterator<E> {
        TODO()
    }

    override fun remove(element: E): Boolean {
        TODO()
    }

    override fun removeAll(elements: Collection<E>): Boolean {
        TODO()
    }

    override fun removeAt(index: Int): E {
        TODO()
    }

    override fun retainAll(elements: Collection<E>): Boolean {
        TODO()
    }

    override fun set(index: Int, element: E): E {
        TODO()
    }

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> {
        TODO()
    }
}

