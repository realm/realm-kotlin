package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmMutableSubscriptionSetPointer
import io.realm.mongodb.sync.BaseSubscriptionSet
import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.Subscription
import io.realm.query.RealmQuery
import kotlin.reflect.KClass

internal class MutableSubscriptionSetImpl<T: BaseRealm>(
    realm: T,
    val nativePointer: RealmMutableSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm, nativePointer), MutableSubscriptionSet {

    override fun <T : RealmObject> add(query: RealmQuery<T>, name: String, updateExisting: Boolean): Subscription {
        TODO("Not yet implemented")
    }

    override fun remove(subscription: Subscription): Boolean {
        TODO()
    }

    override fun remove(name: String): Boolean {
        return RealmInterop.realm_sync_subscriptionset_erase_by_name(nativePointer, name)
    }

    override fun removeAll(objectType: String): Boolean {
        var result = false
        forEach { sub: Subscription ->
            if (sub.objectType == objectType) {
                result = remove(sub) || result
            }
        }
        return result
    }

    override fun <T : RealmObject> removeAll(type: KClass<T>): Boolean {
        var result = false
        val objectType = "" // TODO Map between type and String type
        forEach { sub: Subscription ->
            if (sub.objectType == objectType) {
                result = remove(sub) || result
            }
        }
        return result
    }

    override fun removeAll(): Boolean {
        return RealmInterop.realm_sync_subscriptionset_clear(nativePointer)
    }
}
