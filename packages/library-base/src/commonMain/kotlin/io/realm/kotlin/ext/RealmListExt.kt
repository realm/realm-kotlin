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

package io.realm.kotlin.ext

import io.realm.kotlin.internal.ManagedRealmList
import io.realm.kotlin.internal.UnmanagedRealmList
import io.realm.kotlin.internal.asRealmList
import io.realm.kotlin.internal.query
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.TRUE_PREDICATE
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList

/**
 * Instantiates an **unmanaged** [RealmList].
 */
public fun <T> realmListOf(vararg elements: T): RealmList<T> =
    if (elements.isNotEmpty()) elements.asRealmList() else UnmanagedRealmList()

/**
 * Query the objects of a list by the `filter` and `arguments`.
 *
 * @param filter the Realm Query Language predicate to append.
 * @param arguments Realm values for the predicate.
 */
public fun <T : BaseRealmObject> RealmList<T>.query(
    filter: String = TRUE_PREDICATE,
    vararg arguments: Any?
): RealmQuery<T> =
    if (this is ManagedRealmList) {
        query(filter, arguments)
    } else {
        throw IllegalArgumentException("Unmanaged list cannot be queried")
    }
