package io.realm

import io.realm.base.BaseRealmModel
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

// Implement List instead of MutableList, because you cannot modify this list directly.
class RealmResults<E> : List<E>, OrderedRealmCollection<E> {

    val realm: Realm = TODO()

    // Further filter the result.
    fun where(filter: String = ""): RealmQuery<E> { TODO() }

    // Listen to changes
    fun addChangeListener(listener: (change: OrderedCollectionChange<E, RealmResults<E>>) -> Unit): Cancellable { TODO() }
    fun cancelAllChangeListeners() { TODO() }
    fun observe(): Flow<OrderedCollectionChange<E, RealmResults<E>>> { TODO() }

    fun createSnapshot(): RealmResults<E> { TODO() }

    // Bulk updates
    // Replace all the `setX()` methods with `setValue(value: Any?)`
    // We support 16 different types right now, and will add at least 4 more
    // with the new datatypes. Even though it would technically be more typesafe
    // it feels a little stupid to expose that many types.

    // Postpone or move to helper class
    // fun setValue(property: String, value: Any?) { TODO() }

    // Lets do this
    fun <T> setValue(property: KMutableProperty1<E, T>, value: T?) { TODO() }

    // Utility methods
    fun asJSON(): String { TODO() }
    override fun isManaged(): Boolean { TODO() }
    override fun isValid(): Boolean { TODO() }

    // Interface methods
    override val size: Int get() = TODO()
    override fun contains(element: E): Boolean { TODO() }
    override fun containsAll(elements: Collection<E>): Boolean { TODO() }
    override fun get(index: Int): E { TODO() }
    override fun indexOf(element: E): Int { TODO() }
    override fun isEmpty(): Boolean { TODO() }
    override fun iterator(): MutableIterator<E> { TODO() }
    override fun lastIndexOf(element: E): Int { TODO() }
    override fun listIterator(): ListIterator<E> { TODO() }
    override fun listIterator(index: Int): ListIterator<E> { TODO() }
    override fun subList(fromIndex: Int, toIndex: Int): List<E> { TODO() }

    // OrderedRealmCollection methods
    override fun first(): E { TODO() }
    override fun firstOrDefault(defaultValue: E?): E? { TODO() }
    override fun last(): E { TODO() }
    override fun lastOrDefault(defaultValue: E?): E? { TODO() }
    override fun deleteFromRealm(location: Int) { TODO() }
    override fun deleteFirstFromRealm(): Boolean { TODO() }
    override fun deleteLastFromRealm(): Boolean { TODO() }
    override fun deleteAllFromRealm(): Boolean { TODO() }
}