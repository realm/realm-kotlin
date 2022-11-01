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

package io.realm.kotlin.internal

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * Interface holding default implementation for performing a query on a typed realm.
 */
internal interface InternalTypedRealm : TypedRealm {

    override val configuration: InternalConfiguration
    val realmReference: RealmReference

    override fun <T : BaseRealmObject> query(clazz: KClass<T>, query: String, vararg args: Any?): RealmQuery<T> {
        val className = configuration.mediator.companionOf(clazz).`io_realm_kotlin_className`
        return ObjectQuery(realmReference, realmReference.schemaMetadata.getOrThrow(className).classKey, clazz, configuration.mediator, null, query, *args)
    }

    private fun <T : BaseRealmObject> copyObjectFromRealm(obj: T, depth: Int, closeAfterCopy: Boolean, cache: ManagedToUnmanagedObjectCache): T {
        // Be able to inject a cache here as well, so the Iterable case can share the cache
        if (!obj.isManaged()) {
            throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied: $obj.")
        }
        if (!obj.isValid()) {
            throw IllegalArgumentException(
                "Only valid objects can be copied from Realm. " +
                    "This object was either deleted or the Realm has been closed, making this " +
                    "object invalid: $obj."
            )
        }
        if (depth < 0) {
            throw IllegalArgumentException("Only a depth of 0 or more is allowed. Depth was: $depth.")
        }
        if (obj is RealmObjectInternal) {
            val objectRef: RealmObjectReference<out BaseRealmObject> = obj.io_realm_kotlin_objectReference!!
            val realmRef: RealmReference = objectRef.owner
            val mediator: Mediator = realmRef.owner.configuration.mediator
            val copy = createDetachedCopy(mediator, obj, 0, depth, cache)
            if (closeAfterCopy) {
                objectRef.objectPointer.release()
            }
            return copy
        } else {
            throw IllegalStateException()
        }
    }

    override fun <T : TypedRealmObject> copyFromRealm(obj: T, depth: Int, closeAfterCopy: Boolean): T {
        return copyObjectFromRealm(obj, depth, closeAfterCopy, mutableMapOf())
    }
    override fun <T : TypedRealmObject> copyFromRealm(collection: Iterable<T>, depth: Int, closeAfterCopy: Boolean): List<T> {
        val valid = when (collection) {
            is ManagedRealmList -> collection.isValid()
            is ManagedRealmSet -> collection.isValid()
            else -> true
        }
        if (!valid) {
            throw IllegalArgumentException(
                "Only valid collections can be copied from Realm. " +
                    "This collection was either deleted or the Realm has been closed, making this " +
                    "collection invalid"
            )
        }

        val cache: ManagedToUnmanagedObjectCache = mutableMapOf()
        return if (collection is Collection) {
            // For collections we can pre-allocate the output array
            val iter: Iterator<T> = collection.iterator()
            MutableList(collection.size) { i: Int ->
                copyObjectFromRealm(iter.next(), depth, closeAfterCopy, cache)
            }
        } else {
            // Else we need to just do the naive approach
            val result = ArrayList<T>()
            collection.forEach { obj: T ->
                val copiedObj = copyObjectFromRealm(obj, depth, closeAfterCopy, cache)
                result.add(copiedObj)
            }
            result
        }
    }
}
