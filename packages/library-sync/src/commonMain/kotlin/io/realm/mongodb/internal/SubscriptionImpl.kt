package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.TypedRealm
import io.realm.dynamic.DynamicRealm
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.RealmInstantImpl
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.mongodb.sync.Subscription
import io.realm.query.RealmQuery
import kotlin.reflect.KClass

internal class SubscriptionImpl(
    private val realm: BaseRealm,
    private val parentNativePointer: RealmBaseSubscriptionSetPointer,
    internal val nativePointer: RealmSubscriptionPointer
) : Subscription {
    override val createdAt: RealmInstant
        get() = RealmInstantImpl(RealmInterop.realm_sync_subscription_created_at(nativePointer))
    override val updatedAt: RealmInstant
        get() = RealmInstantImpl(RealmInterop.realm_sync_subscription_updated_at(nativePointer))
    override val name: String?
        get() {
            return RealmInterop.realm_sync_subscription_name(nativePointer)
        }
    override val objectType: String
        get() = RealmInterop.realm_sync_subscription_object_class_name(nativePointer)
    override val queryDescription: String
        get() = RealmInterop.realm_sync_subscription_query_string(nativePointer)

    override fun <T : RealmObject> asQuery(type: KClass<T>): RealmQuery<T> {
        // TODO Check for invalid combinations of Realm and type once we properly support
        // DynamicRealm
        return when (realm) {
            is TypedRealm -> {
                val companion = realmObjectCompanionOrThrow(type)
                val userTypeName = companion.`io_realm_kotlin_className`
                if (userTypeName != objectType) {
                    throw IllegalArgumentException(
                        "Wrong query type. This subscription is for " +
                            "objects of type: $objectType, but $userTypeName was provided as input."
                    )
                }
                realm.query(type, queryDescription)
            }
            is DynamicRealm -> {
                if (type != DynamicRealmObject::class) {
                    throw IllegalArgumentException(
                        "This subscription was fetched from a " +
                            "DynamicRealm, so the type argument must be `DynamicRealmObject`."
                    )
                }
                realm.query(className = objectType, query = queryDescription) as RealmQuery<T>
            }
            else -> {
                TODO("Unsupported Realm type: ${realm::class}")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as SubscriptionImpl

        val version = RealmInterop.realm_sync_subscriptionset_version(parentNativePointer)
        val otherVersion = RealmInterop.realm_sync_subscriptionset_version(other.parentNativePointer)
        if (version != otherVersion) return false
        val id = RealmInterop.realm_sync_subscription_id(nativePointer)
        val otherId = RealmInterop.realm_sync_subscription_id(nativePointer)
        if (id != otherId) return false

        return true
    }

    override fun hashCode(): Int {
        val id = RealmInterop.realm_sync_subscription_id(nativePointer)
        val version = RealmInterop.realm_sync_subscriptionset_version(parentNativePointer)
        var result = id.hashCode()
        result = 31 * result + version.toInt()
        return result
    }
}
