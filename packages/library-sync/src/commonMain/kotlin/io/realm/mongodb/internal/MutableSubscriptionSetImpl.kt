package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmObject
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmMutableSubscriptionSetPointer
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.internal.query.ObjectQuery
import io.realm.mongodb.sync.MutableSubscriptionSet
import io.realm.mongodb.sync.Subscription
import io.realm.query.RealmQuery
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlin.reflect.KClass

internal class MutableSubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    nativePointer: RealmMutableSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm), MutableSubscriptionSet {

    override val nativePointer: AtomicRef<RealmMutableSubscriptionSetPointer> = atomic(nativePointer)

    override fun <T : RealmObject> add(query: RealmQuery<T>, name: String?, updateExisting: Boolean): Subscription {
        // If an existing Subscription already exists, just return that one instead.
        val existingSub: Subscription? = if (name != null) findByName(name) else findByQuery(query)
        existingSub?.let {
            // TODO Remove trim once https://github.com/realm/realm-core/issues/5504 is fixed
            if (name == existingSub.name && query.description().trim() == existingSub.queryDescription.trim()) {
                return existingSub
            }
        }
        val (ptr, inserted) = RealmInterop.realm_sync_subscriptionset_insert_or_assign(
            nativePointer.value,
            (query as ObjectQuery).queryPointer,
            name
        )
        if (!updateExisting && !inserted) {
            // This will also cancel the entire update
            throw IllegalArgumentException(
                // Only named queries will run into this, so it is safe to reference the name.
                "Existing query '$name' was found and could not be updated as " +
                    "`updateExisting = false`"
            )
        }

        return SubscriptionImpl(realm, nativePointer.value, ptr)
    }

    override fun remove(subscription: Subscription): Boolean {
        return RealmInterop.realm_sync_subscriptionset_erase_by_id(nativePointer.value, (subscription as SubscriptionImpl).nativePointer)
    }

    override fun remove(name: String): Boolean {
        return RealmInterop.realm_sync_subscriptionset_erase_by_name(nativePointer.value, name)
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
        val objectType = realmObjectCompanionOrThrow(type).`io_realm_kotlin_className`
        forEach { sub: Subscription ->
            if (sub.objectType == objectType) {
                result = remove(sub) || result
            }
        }
        return result
    }

    override fun removeAll(): Boolean {
        return RealmInterop.realm_sync_subscriptionset_clear(nativePointer.value)
    }
}
