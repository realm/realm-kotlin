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

package io.realm.kotlin.query

/**
 * Queries that return scalar, nullable values. This type of query is used to more accurately
 * represent the results provided by some query operations, e.g. [RealmQuery.min] or
 * [RealmQuery.max].
 */
public interface RealmScalarNullableQuery<T> : RealmScalarQuery<T?>

/**
 * Similar to [RealmScalarNullableQuery.find] but it receives a [block] in which the scalar result
 * from the query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
public fun <T, R> RealmScalarNullableQuery<T>.find(block: (T?) -> R): R = find().let(block)
