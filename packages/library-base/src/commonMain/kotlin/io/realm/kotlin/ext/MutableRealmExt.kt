/*
 * Copyright 2022 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm.kotlin.ext

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject

/**
 * Returns a [RealmQuery] matching the predicate represented by [query].
 *
 * Reified convenience wrapper for [MutableRealm.query].
 */
public inline fun <reified T : BaseRealmObject> MutableRealm.query(
    query: String = "TRUEPREDICATE",
    vararg args: Any?
): RealmQuery<T> = query(T::class, query, *args)
