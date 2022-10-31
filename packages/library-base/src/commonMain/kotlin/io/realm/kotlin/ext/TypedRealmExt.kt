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

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmObject

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper of [TypedRealm.query].
 *
 * @param query the Realm Query Language predicate to append.
 * @param args Realm values for the predicate.
 */
public inline fun <reified T : BaseRealmObject> TypedRealm.query(
    query: String = "TRUEPREDICATE",
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)

/**
 * TODO
 *
 * @param depth TODO
 * @param closeAfterCopy TODO
 * @throws IllegalArgumentException TODO
 * @return TODO
 */
public inline fun <reified T : RealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth, closeAfterCopy)
    } ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
