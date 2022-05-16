package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.TypedRealm
import io.realm.dynamic.DynamicRealm
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.RealmInstantImpl
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.mongodb.sync.Subscription
import io.realm.query.RealmQuery
import kotlin.reflect.KClass

internal class SubscriptionImpl(private val realm: BaseRealm, private val nativePointer: RealmSubscriptionPointer) : Subscription {
    override val createdAt: RealmInstant
        get() = RealmInstantImpl(RealmInterop.realm_sync_subscription_created_at(nativePointer))
    override val updatedAt: RealmInstant
        get() = RealmInstantImpl(RealmInterop.realm_sync_subscription_updated_at(nativePointer))
    override val name: String?
        get() {
            // "" is not an allowed name in Core, so can safely be mapped to null.
            return RealmInterop.realm_sync_subscription_name(nativePointer).ifEmpty { null }
        }
    override val objectType: String
        get() = RealmInterop.realm_sync_subscription_object_class_name(nativePointer)
    override val queryDescription: String
        get() = RealmInterop.realm_sync_subscription_query_string(nativePointer)

    override fun <T : RealmObject> asQuery(type: KClass<T>): RealmQuery<T> {
        // TODO Check for invalid combinations of Realm and type
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
}
