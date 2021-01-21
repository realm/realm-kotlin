package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

class RealmList<E>(override val size: Int = 0): MutableList<E>, OrderedRealmCollection<E> {

    // Further filter the result.
    fun where(predicate: String = ""): RealmQuery<E> { TODO() }

    override fun contains(element: E): Boolean { TODO() }
    override fun containsAll(elements: Collection<E>): Boolean { TODO() }
    override fun get(index: Int): E { TODO() }
    override fun indexOf(element: E): Int { TODO() }
    override fun isEmpty(): Boolean { TODO() }
    override fun iterator(): MutableIterator<E> { TODO() }
    override fun lastIndexOf(element: E): Int { TODO() }
    override fun add(element: E): Boolean { TODO() }
    override fun add(index: Int, element: E) { TODO() }
    override fun addAll(index: Int, elements: Collection<E>): Boolean { TODO() }
    override fun addAll(elements: Collection<E>): Boolean { TODO() }
    override fun clear() { TODO() }
    override fun listIterator(): MutableListIterator<E> { TODO() }
    override fun listIterator(index: Int): MutableListIterator<E> { TODO() }
    override fun remove(element: E): Boolean { TODO() }
    override fun removeAll(elements: Collection<E>): Boolean { TODO() }
    override fun removeAt(index: Int): E { TODO() }
    override fun retainAll(elements: Collection<E>): Boolean { TODO() }
    override fun set(index: Int, element: E): E { TODO() }
    override fun subList(fromIndex: Int, toIndex: Int): MutableList<E> { TODO() }
    override fun first(): E { TODO() }
    override fun firstOrDefault(defaultValue: E?): E? { TODO() }
    override fun last(): E { TODO() }
    override fun lastOrDefault(defaultValue: E?): E? { TODO() }
    override fun deleteFromRealm(location: Int) { TODO() }
    override fun deleteFirstFromRealm(): Boolean { TODO() }
    override fun deleteLastFromRealm(): Boolean { TODO() }
    override fun deleteAllFromRealm(): Boolean { TODO() }
}

