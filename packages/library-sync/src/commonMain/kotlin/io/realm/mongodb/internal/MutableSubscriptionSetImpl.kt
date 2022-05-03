package io.realm.mongodb.internal

import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.Subscription
import kotlin.reflect.KClass
import io.realm.query.RealmQuery
import io.realm.RealmObject
import io.realm.BaseRealm
import io.realm.internal.interop.RealmMutableSubscriptionSetPointer

internal class MutableSubscriptionSetImpl(realm: BaseRealm, val nativePointer: RealmMutableSubscriptionSetPointer): BaseSubscriptionSetImpl(realm, nativePointer), MutableSubscriptionSet {
    override fun <T: RealmObject> add(query: RealmQuery<T>, name: String, updateExisting: Boolean): Subscription {
        TODO("Not yet implemented")
    }

    override fun remove(subscription: Subscription): Boolean {
        TODO("Not yet implemented")
    }

    override fun remove(name: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(objectType: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun <T: RealmObject> removeAll(type: KClass<T>): Boolean {
        TODO("Not yet implemented")
    }

    override fun removeAll(): Boolean {
        TODO("Not yet implemented")
    }
}