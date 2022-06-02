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

import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow

/**
 * Queries that return scalar values. This type of query is used to more accurately represent the
 * results provided by some query operations, e.g. [RealmQuery.count] or [RealmQuery.sum].
 */
public interface RealmScalarQuery<T> {
    /**
     * Returns the value of a scalar query as a [T] in a blocking fashion. The result may be of a
     * different type depending on the type of query:
     *
     * - `[count]` returns [Long]
     * - `[sum]` returns the `type` specified in the call to said function
     *
     * It is not recommended launching heavy queries from the UI thread as it may result in a drop
     * of frames or even ANRs. Use [asFlow] to obtain results of such queries asynchroneously instead.
     *
     * @return a [T] containing the result of the scalar query.
     */
    public fun find(): T

    /**
     * Calculates the value that fulfills the query conditions and returns it asynchronously as a
     * [Flow].
     *
     * If there is any changes to the objects represented by the query backing the value, the flow
     * will emit the updated value. The flow will continue running indefinitely until canceled.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the [RealmResults] resulting from running this query.
     *
     * @throws UnsupportedOperationException if called on a query issued on a [MutableRealm].
     */
    public fun asFlow(): Flow<T>
}

/**
 * Similar to [RealmScalarQuery.find] but it receives a [block] in which the scalar result from the
 * query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
public fun <T, R> RealmScalarQuery<T>.find(block: (T) -> R): R = find().let(block)

/**
 * Similar to [RealmSingleQuery.find] but it receives a [block] in which the [RealmObject] or
 * [EmbeddedRealmObject] from the query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
public fun <T : BaseRealmObject, R> RealmSingleQuery<T>.find(block: (T?) -> R): R = find().let(block)
