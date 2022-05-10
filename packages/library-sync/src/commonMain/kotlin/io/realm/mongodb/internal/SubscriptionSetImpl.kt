package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.internal.interop.RealmSubscriptionSetPointer
import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.SubscriptionSet
import kotlin.time.Duration
import io.realm.internal.interop.RealmInterop

internal class SubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    var nativePointer: RealmSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm, nativePointer), SubscriptionSet<T> {

    override suspend fun update(block: MutableSubscriptionSet.(realm: T) -> Unit): SubscriptionSet<T> {
        val ptr = RealmInterop.realm_sync_make_subscriptionset_mutable(nativePointer)
        val mut = MutableSubscriptionSetImpl(realm, ptr)
        mut.block(realm)
        nativePointer = RealmInterop.realm_sync_subscriptionset_commit(ptr)
        return this
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
        TODO("Not yet implemented")
    }

    override fun refresh(): SubscriptionSet<T> {
        RealmInterop.realm_sync_subscriptionset_refresh(nativePointer)
        return this
    }
}
