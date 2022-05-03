package io.realm.mongodb.internal

import io.realm.mongodb.sync.BaseSubscriptionSet
import io.realm.mongodb.sync.Subscription
import io.realm.mongodb.sync.SubscriptionSetState
import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.query.RealmQuery
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer

internal abstract class BaseSubscriptionSetImpl(
    private val realm: BaseRealm,
    private val nativePointer: RealmBaseSubscriptionSetPointer
) : BaseSubscriptionSet {

    override fun <T : RealmObject> findByQuery(query: RealmQuery<T>): Subscription? {
        TODO()
    }

    override fun findByName(name: String): Subscription? {
        TODO()
    }

    override var state: SubscriptionSetState = SubscriptionSetState.BOOTSTRAPPING
        get() = TODO()

    override var errorMessage: String? = null
        get() = TODO()

    override var size: Int = 0
        get() = TODO("Not yet implemented")

    override fun iterator(): Iterator<Subscription> {
        TODO("Not yet implemented")
    }
}
