package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.internal.BaseRealmImpl
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

    override fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription? {
        val queryPointer = (query as ObjectQuery).queryPointer
        val sub: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_query(
            nativePointer.value,
            queryPointer
        )
        return if (sub == null) null else SubscriptionImpl(realm, nativePointer.value, sub)
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
        return object : Iterator<Subscription> {
            // TODO Is there a way to clone a subscription set at a given version
            //  for now use the latest version instead.
            private val nativePointer = RealmInterop.realm_sync_get_latest_subscriptionset(
                (realm as BaseRealmImpl).realmReference.dbPointer
            )
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
