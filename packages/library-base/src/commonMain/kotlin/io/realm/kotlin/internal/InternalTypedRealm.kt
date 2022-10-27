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
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.query.ObjectQuery
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
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

    override fun <T : BaseRealmObject> copyFromRealm(obj: T, depth: Int, closeAfterCopy: Boolean): T {
        // Be able to inject a cache here as well, so the Iterable case can share the cache
        if (obj is RealmObjectInternal) {
            val objectRef: RealmObjectReference<out BaseRealmObject> = obj.io_realm_kotlin_objectReference!!
            val realmRef: RealmReference = objectRef.owner
            val mediator: Mediator = realmRef.owner.configuration.mediator
            val copy = createDetachedCopy(mediator, realmRef, obj, depth)
            if (closeAfterCopy) {
                RealmInterop.realm_release(objectRef.objectPointer)
            }
            return copy
        } else {
            throw IllegalStateException(
                "Object has not been modified by the Realm Compiler " +
                    "Plugin. Has the Realm Gradle Plugin been applied to the project with this " +
                    "model class?"
            )
        }
    }
    override fun <T : BaseRealmObject> copyFromRealm(obj: Iterable<T>, depth: Int, closeAfterCopy: Boolean): List<T> {
        TODO() // Cache must be shared for all objects
    }
}
