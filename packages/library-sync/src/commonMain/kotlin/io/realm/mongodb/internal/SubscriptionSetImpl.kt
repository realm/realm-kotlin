package io.realm.mongodb.internal

import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.SubscriptionSet
import kotlin.time.Duration
import io.realm.internal.interop.RealmSubscriptionSetPointer
import io.realm.BaseRealm
import io.realm.Realm

internal class SubscriptionSetImpl(
    realm: BaseRealm,
    val nativePointer: RealmSubscriptionSetPointer
) : BaseSubscriptionSetImpl(realm, nativePointer), SubscriptionSet {

    override suspend fun update(block: MutableSubscriptionSet.(realm: Realm) -> Unit): SubscriptionSet {
        TODO()
    }

    override suspend fun waitForSynchronization(timeout: Duration): Boolean {
        TODO("Not yet implemented")
    }

    override fun refresh(): SubscriptionSet {
        TODO("Not yet implemented")
    }
}