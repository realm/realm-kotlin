/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.mongodb.internal

import io.realm.BaseRealm
import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.TypedRealm
import io.realm.internal.RealmInstantImpl
import io.realm.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSubscriptionPointer
import io.realm.mongodb.sync.Subscription
import io.realm.query.RealmQuery
import kotlin.reflect.KClass

internal class SubscriptionImpl(
    private val realm: BaseRealm,
    private val parentNativePointer: RealmBaseSubscriptionSetPointer,
    internal val nativePointer: RealmSubscriptionPointer
) : Subscription {
    override val createdAt: RealmInstant = RealmInstantImpl(RealmInterop.realm_sync_subscription_created_at(nativePointer))
    override val updatedAt: RealmInstant = RealmInstantImpl(RealmInterop.realm_sync_subscription_updated_at(nativePointer))
    override val name: String? = RealmInterop.realm_sync_subscription_name(nativePointer)
    override val objectType: String = RealmInterop.realm_sync_subscription_object_class_name(nativePointer)
    override val queryDescription: String = RealmInterop.realm_sync_subscription_query_string(nativePointer)

    @Suppress("invisible_member")
    override fun <T : RealmObject> asQuery(type: KClass<T>): RealmQuery<T> {
        // TODO Check for invalid combinations of Realm and type once we properly support
        // DynamicRealm
        return when (realm) {
            is TypedRealm -> {
                val companion = io.realm.internal.platform.realmObjectCompanionOrThrow(type)
                val userTypeName = companion.`io_realm_kotlin_className`
                if (userTypeName != objectType) {
                    throw IllegalArgumentException(
                        "Wrong query type. This subscription is for " +
                            "objects of type: $objectType, but $userTypeName was provided as input."
                    )
                }
                realm.query(type, queryDescription)
            }
            // is DynamicRealm -> {
            //     if (type != DynamicRealmObject::class) {
            //         throw IllegalArgumentException(
            //             "This subscription was fetched from a " +
            //                 "DynamicRealm, so the type argument must be `DynamicRealmObject`."
            //         )
            //     }
            //     realm.query(className = objectType, query = queryDescription) as RealmQuery<T>
            // }
            else -> {
                IllegalStateException("Unsupported Realm type: ${realm::class}")
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
