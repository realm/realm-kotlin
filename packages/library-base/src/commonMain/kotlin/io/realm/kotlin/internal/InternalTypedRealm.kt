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
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.schema.RealmSchema
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass

/**
 * Interface holding default implementation for performing a query on a typed realm.
 */
internal interface InternalTypedRealm : TypedRealm {

    override val configuration: InternalConfiguration
    val realmReference: RealmReference

    // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
    //  update, but requires that we initialize it all on the actual schema update to allow freezing
    //  it. If we make the schema backed by the actual realm_class_info_t/realm_property_info_t
    //  initialization it would probably be acceptable to initialize on schema updates
    override fun schema(): RealmSchema {
        return RealmSchemaImpl.fromTypedRealm(
            realmReference.dbPointer,
            realmReference.schemaMetadata
        )
    }

    override fun <T : TypedRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> {
        val className = configuration.mediator.companionOf(clazz).`io_realm_kotlin_className`
        return ObjectQuery(
            realmReference,
            realmReference.schemaMetadata.getOrThrow(className).classKey,
            clazz,
            configuration.mediator,
            query,
            args
        )
    }

    private fun <T : BaseRealmObject> copyObjectFromRealm(
        obj: T,
        depth: UInt,
        closeAfterCopy: Boolean,
        cache: ManagedToUnmanagedObjectCache
    ): T {
        // Be able to inject a cache here as well, so the Iterable case can share the cache
        if (!obj.isManaged()) {
            throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied: $obj.")
        }
        if (!obj.isValid()) {
            throw IllegalArgumentException(
                "Only valid objects can be copied from Realm. " +
                    "This object was either deleted, closed or its Realm has been closed, making " +
                    "this object invalid: $obj."
            )
        }
        if (obj is RealmObjectInternal) {
            val objectRef: RealmObjectReference<out BaseRealmObject> =
                obj.io_realm_kotlin_objectReference!!
            val realmRef: RealmReference = objectRef.owner
            val mediator: Mediator = realmRef.owner.configuration.mediator
            return createDetachedCopy(mediator, obj, 0.toUInt(), depth, closeAfterCopy, cache)
        } else {
            throw MISSING_PLUGIN
        }
    }

    override fun <T : TypedRealmObject> copyFromRealm(obj: T, depth: UInt): T {
        return copyObjectFromRealm(obj, depth, closeAfterCopy = false, mutableMapOf())
    }

    override fun <T : TypedRealmObject> copyFromRealm(
        collection: Iterable<T>,
        depth: UInt
    ): List<T> {
        val valid: Boolean = when (collection) {
            is ManagedRealmList -> collection.isValid()
            is ManagedRealmSet -> collection.isValid()
            is RealmResultsImpl -> !collection.realm.isClosed()
            else -> true
        }
        if (!valid) {
            throw IllegalArgumentException(
                "Only valid collections can be copied from Realm. " +
                    "This collection was either deleted, closed or its Realm " +
                    "has been closed, making this collection invalid."
            )
        }
        val cache: ManagedToUnmanagedObjectCache = mutableMapOf()
        return if (collection is Collection) {
            // For collections we can pre-allocate the output array
            val iter: Iterator<T> = collection.iterator()
            MutableList(collection.size) {
                copyObjectFromRealm(iter.next(), depth, closeAfterCopy = false, cache)
            }
        } else {
            // Else we need to just do the naive approach
            val result = ArrayList<T>()
            collection.forEach { obj: T ->
                val copiedObj = copyObjectFromRealm(obj, depth, closeAfterCopy = false, cache)
                result.add(copiedObj)
            }
            result
        }
    }

    override fun <T : TypedRealmObject> copyFromRealm(
        dictionary: RealmDictionary<T?>,
        depth: UInt
    ): Map<String, T?> {
        val valid = when (dictionary) {
            is ManagedRealmDictionary -> dictionary.isValid()
            else -> true
        }
        if (!valid) {
            throw IllegalArgumentException(
                "Only valid collections can be copied from Realm. " +
                    "This collection was either deleted, closed or its Realm " +
                    "has been closed, making this collection invalid."
            )
        }
        val cache: ManagedToUnmanagedObjectCache = mutableMapOf()
        val entries = dictionary.map {
            val entryValue = it.value
            val copiedObject = when (entryValue != null) {
                true -> copyObjectFromRealm(entryValue, depth, false, cache)
                false -> null
            }
            Pair(it.key, copiedObject)
        }
        return realmDictionaryOf(entries)
    }
}
