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

package io.realm.internal

import io.realm.BaseRealmObject
import io.realm.TypedRealm
import io.realm.internal.query.ObjectQuery
import io.realm.query.RealmQuery
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
}
