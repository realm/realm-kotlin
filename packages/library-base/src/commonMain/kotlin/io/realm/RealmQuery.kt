/*
 * Copyright 2021 Realm Inc.
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
 *
 */

package io.realm

import io.realm.internal.Flowable
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Queries returning [RealmResults].
 */
interface RealmElementQuery<E> : Flowable<RealmResults<E>> {
    fun find(): RealmResults<E>

    // TODO missing fine-grained notifications
    override fun asFlow(): Flow<RealmResults<E>>
}

/**
 * Queries returning a single object.
 * NOTE: The interaction with primitive queries might be a bit akward
 */
// TODO : query should this be done using the raw query string
interface RealmSingleQuery<E> : Flowable<E> {
    fun find(): E?
    // When fine-grained notifications are merged
    // fun asFlow(): Flow<ObjectChange<E>>
}

/**
 * Queries that return scalar values. We cannot express in the type-system which scalar values
 * we support, so this checks must be done by the compiler plugin or at runtime.
 */
interface RealmScalarQuery<E> : Flowable<E> {
    fun find(): E?

    // TODO : query - not sure the comment below is possible, ask!
    // These will require a few hacks as Core changelisteners do not support these currently.
    // Easy work-around is just calling `Results.<aggregateFunction>()` inside the NotifierThread
//    fun asFlow(): Flow<E>
}

/**
 * TODO : query
 */
interface RealmQuery<E> : RealmElementQuery<E> {

    fun query(filter: String, vararg arguments: Any?): RealmQuery<E>
    fun sort(property: String, sortOrder: QuerySort = QuerySort.ASCENDING): RealmQuery<E>
    fun sort(
        propertyAndSortOrder: Pair<String, QuerySort>,
        vararg additionalPropertiesAndOrders: Pair<String, QuerySort>
    ): RealmQuery<E>

    fun distinct(property: String): RealmQuery<E>
    fun limit(results: Int): RealmQuery<E>

    // FIXME Is there a better way to force the constraints here. Restricting RealmQuery<E> to
    //  RealmObjects would help, but would prevent this class from being used by primitive queries.
    //  We need to investigate what other SDK's do here
    fun first(): RealmSingleQuery<E>

    fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun average(property: String): RealmScalarQuery<Double>
    fun count(): RealmScalarQuery<Long>
}

inline fun <reified T : Any> RealmQuery<*>.min(property: String): RealmScalarQuery<T> =
    min(property, T::class)

inline fun <reified T : Any> RealmQuery<*>.max(property: String): RealmScalarQuery<T> =
    max(property, T::class)

inline fun <reified T : Any> RealmQuery<*>.sum(property: String): RealmScalarQuery<T> =
    sum(property, T::class)

inline fun <reified T : Any> RealmQuery<*>.average(property: String): RealmScalarQuery<T> =
    average(property, T::class)

/**
 * Similar to [RealmQuery.find] but it receives a block where the [RealmResults] from te query are
 * provided.
 */
fun <T> RealmQuery<T>.find(block: (RealmResults<T>) -> Unit): RealmResults<T> = find().let {
    block.invoke(it)
    it
}

/**
 * Similar to [RealmScalarQuery.find] but it receives a block where the scalar [T] from te query
 * is provided.
 */
fun <T> RealmScalarQuery<T>.find(block: (T?) -> Unit): T? = find().let {
    block.invoke(it)
    it
}

/**
 * TODO : query
 */
enum class QuerySort {
    ASCENDING,
    DESCENDING
}

/**
 * TODO : query
 */
internal class RealmSingleQueryImpl<E : RealmObject> : RealmSingleQuery<E> {
    override fun find(): E? {
        TODO("Not yet implemented")
    }

    override fun asFlow(): Flow<E?> {
        TODO("Not yet implemented")
    }
}
