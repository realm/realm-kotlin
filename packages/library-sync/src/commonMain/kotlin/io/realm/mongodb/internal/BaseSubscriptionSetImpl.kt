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

internal abstract class BaseSubscriptionSetImpl<T : BaseRealm>(
    protected val realm: T,
    private val nativePointer: RealmBaseSubscriptionSetPointer
) : BaseSubscriptionSet {

    override fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription? {
        val queryPointer = (query as ObjectQuery).queryPointer
        val sub: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_query(
            nativePointer,
            queryPointer
        )
        return if (sub == null) null else SubscriptionImpl(realm, nativePointer, sub)
    }

    override fun findByName(name: String): Subscription? {
        val sub: RealmSubscriptionPointer? = RealmInterop.realm_sync_find_subscription_by_name(
            nativePointer,
            name
        )
        return if (sub == null) null else SubscriptionImpl(realm, nativePointer, sub)
    }

    override val state: SubscriptionSetState
        get() {
            val state = RealmInterop.realm_sync_subscriptionset_state(nativePointer)
            return SubscriptionSetState.from(state)
        }

    override val errorMessage: String?
        get() = RealmInterop.realm_sync_subscriptionset_error_str(nativePointer)

    override val size: Int
        get() = RealmInterop.realm_sync_subscriptionset_size(nativePointer).toInt()

    override fun iterator(): Iterator<Subscription> {
        return object : Iterator<Subscription> {
            // TODO Is there a way to clone a subscription set at a given version
            //  for now use the latest version instead.
            private val nativePointer = RealmInterop.realm_sync_get_latest_subscriptionset(
                (realm as BaseRealmImpl).realmReference.dbPointer
            )
            private val size = RealmInterop.realm_sync_subscriptionset_size(nativePointer)
            private var currentIndex = -1L

            override fun hasNext(): Boolean {
                return size > 0 && currentIndex != size
            }

            override fun next(): Subscription {
                currentIndex++
                val ptr = RealmInterop.realm_sync_subscription_at(nativePointer, currentIndex)
                return SubscriptionImpl(realm, nativePointer, ptr)
            }
        }
    }
}
