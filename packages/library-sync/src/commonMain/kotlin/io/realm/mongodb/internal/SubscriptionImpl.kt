package io.realm.mongodb.internal

import io.realm.mongodb.sync.Subscription
import io.realm.RealmObject
import io.realm.query.RealmQuery
import io.realm.RealmInstant
import kotlin.reflect.KClass

internal class SubscriptionImpl: Subscription {
    override val createdAt: RealmInstant
        get() = TODO("Not yet implemented")
    override val updatedAt: RealmInstant
        get() = TODO("Not yet implemented")
    override val name: String?
        get() = TODO("Not yet implemented")
    override val objectType: String
        get() = TODO("Not yet implemented")
    override val queryDescription: String
        get() = TODO("Not yet implemented")
    override fun <T: RealmObject> asQuery(type: KClass<T>) : RealmQuery<T> {
        TODO()
    }
}

