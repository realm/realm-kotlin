package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.internal.query.ObjectQuery
import io.realm.mongodb.sync.BaseSubscriptionSet
import io.realm.mongodb.sync.Subscription
import io.realm.mongodb.sync.SubscriptionSetState
import io.realm.query.RealmQuery
import kotlinx.atomicfu.AtomicRef

internal abstract class BaseSubscriptionSetImpl<T : BaseRealm>(
    protected val realm: T,
) : BaseSubscriptionSet {

    protected abstract val nativePointer: AtomicRef<out RealmBaseSubscriptionSetPointer>
    protected abstract fun getIteratorSafePointer(): RealmBaseSubscriptionSetPointer

    override fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription? {
        val queryPointer = (query as ObjectQuery).queryPointer
        val subscriptionPointer: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_query(
            nativePointer.value,
            queryPointer
        )
        return if (subscriptionPointer == null)
            null
        else
            SubscriptionImpl(realm, nativePointer.value, subscriptionPointer)
    }

    override fun findByName(name: String): Subscription? {
        val sub: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_name(
            nativePointer.value,
            name
        )
        return if (sub == null) null else SubscriptionImpl(realm, nativePointer.value, sub)
    }

    override val state: SubscriptionSetState
        get() {
            val state = RealmInterop.realm_sync_subscriptionset_state(nativePointer.value)
            return SubscriptionSetState.from(state)
        }

    override val errorMessage: String?
        get() = RealmInterop.realm_sync_subscriptionset_error_str(nativePointer.value)

    override val size: Int
        get() = RealmInterop.realm_sync_subscriptionset_size(nativePointer.value).toInt()

    override fun iterator(): Iterator<Subscription> {

        // We want to keep iteration stable even if a SubscriptionSet is refreshed
        // during iteration. In order to do so, the iterator needs to own the pointer.
        // But since here doesn't seem to be a way to clone a subscription set at a
        // given version we use the latest version instead.
        //
        // This means there is small chance the set of subscriptions is different
        // than the one you called `iterator` on, but since that point to a race
        // condition in the users logic, we accept it.
        //
        // For MutableSubscriptionSets, we just re-use the pointer as there is no
        // API to refresh the set. It is still possible to get odd results if you
        // add subscriptions during iteration, but this is no different than any
        // other iterator.
        val iteratorPointer = getIteratorSafePointer()

        return object : Iterator<Subscription> {
            private val nativePointer: RealmBaseSubscriptionSetPointer = iteratorPointer
            private var cursor = 0L
            private val size: Long = RealmInterop.realm_sync_subscriptionset_size(nativePointer)

            override fun hasNext(): Boolean {
                return cursor < size
            }

            override fun next(): Subscription {
                if (cursor >= size) {
                    throw NoSuchElementException(
                        "Iterator has no more elements. " +
                            "Tried index " + cursor + ". Size is " + size + "."
                    )
                }
                val ptr = RealmInterop.realm_sync_subscription_at(nativePointer, cursor)
                cursor++
                return SubscriptionImpl(realm, nativePointer, ptr)
            }
        }
    }
}
