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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.BaseRealm
import io.realm.kotlin.internal.interop.RealmBaseSubscriptionSetPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmMutableSubscriptionSetPointer
import io.realm.kotlin.mongodb.sync.MutableSubscriptionSet
import io.realm.kotlin.mongodb.sync.Subscription
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass

internal class MutableSubscriptionSetImpl<T : BaseRealm>(
    realm: T,
    nativePointer: RealmMutableSubscriptionSetPointer
) : BaseSubscriptionSetImpl<T>(realm), MutableSubscriptionSet {

    override val nativePointer: RealmMutableSubscriptionSetPointer = nativePointer

    override fun getIteratorSafePointer(): RealmBaseSubscriptionSetPointer {
        return nativePointer
    }

    @Suppress("invisible_reference", "invisible_member")
    override fun <T : RealmObject> add(query: RealmQuery<T>, name: String?, updateExisting: Boolean): Subscription {
        // If an existing Subscription already exists, just return that one instead.
        val existingSub: Subscription? = if (name != null) findByName(name) else findByQuery(query)
        existingSub?.let {
            // Depending on how descriptors are added to the Query, the amount of whitespace in the
            // `description()` might vary from what is reported by the Subscription, so we need
            // to trim both to ensure a consistent result.
            if (name == existingSub.name && query.description().trim() == existingSub.queryDescription.trim()) {
                return existingSub
            }
        }
        val (ptr, inserted) = RealmInterop.realm_sync_subscriptionset_insert_or_assign(
            nativePointer,
            (query as io.realm.kotlin.internal.query.ObjectQuery).queryPointer,
            name
        )
        if (!updateExisting && !inserted) {
            // This will also cancel the entire update
            throw IllegalStateException(
                // Only named queries will run into this, so it is safe to reference the name.
                "Existing query '$name' was found and could not be updated as " +
                    "`updateExisting = false`"
            )
        }

        return SubscriptionImpl(realm, nativePointer, ptr)
    }

    override fun remove(subscription: Subscription): Boolean {
        return RealmInterop.realm_sync_subscriptionset_erase_by_id(nativePointer, (subscription as SubscriptionImpl).nativePointer)
    }

    override fun remove(name: String): Boolean {
        return RealmInterop.realm_sync_subscriptionset_erase_by_name(nativePointer, name)
    }

    override fun removeAll(objectType: String): Boolean {
        if (realm.schema().get(objectType) == null) {
            throw IllegalArgumentException("'$objectType' is not part of the schema for this Realm: ${realm.configuration.path}")
        }
        var result = false
        forEach { sub: Subscription ->
            if (sub.objectType == objectType) {
                result = remove(sub) || result
            }
        }
        return result
    }

    @Suppress("invisible_member")
    override fun <T : RealmObject> removeAll(type: KClass<T>): Boolean {
        var result = false
        val objectType = io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow(type).`io_realm_kotlin_className`
        if (realm.schema().get(objectType) == null) {
            throw IllegalArgumentException("'$type' is not part of the schema for this Realm: ${realm.configuration.path}")
        }
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
