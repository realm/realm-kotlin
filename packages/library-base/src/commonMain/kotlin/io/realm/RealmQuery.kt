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

    /**
     * Sorts the query result by the specific property name according to [sortOrder], which is
     * [QuerySort.ASCENDING] by default.
     *
     * Sorting is currently limited to character sets in 'Latin Basic', 'Latin Supplement',
     * 'Latin Extended A', and 'Latin Extended B' (UTF-8 range 0-591). For other character sets
     * sorting will have no effect.
     *
     * @param property the property name to sort by.
     * @throws [IllegalArgumentException] if the property name does not exist.
     */
//     * @throws [IllegalStateException] if a sorting order was already defined.
    fun sort(property: String, sortOrder: QuerySort = QuerySort.ASCENDING): RealmQuery<E>

    /**
     * Sorts the query result by the specific property name according to [Pair]s of properties and
     * sorting order.
     *
     * Sorting is currently limited to character sets in 'Latin Basic', 'Latin Supplement',
     * 'Latin Extended A', and 'Latin Extended B' (UTF-8 range 0-591). For other character sets
     * sorting will have no effect.
     *
     * @param propertyAndSortOrder pair containing the property and the order to sort by.
     * @throws [IllegalArgumentException] if the property name does not exist.
     */
//     * @throws [IllegalStateException] if a sorting order was already defined.
    fun sort(
        propertyAndSortOrder: Pair<String, QuerySort>,
        vararg additionalPropertiesAndOrders: Pair<String, QuerySort>
    ): RealmQuery<E>

    /**
     * Selects a distinct set of objects of a specific class. When multiple distinct fields are
     * given, all unique combinations of values in the fields will be returned. In case of multiple
     * matches, it is undefined which object is returned. Unless the result is sorted, then the
     * first object will be returned.
     *
     * @param firstFieldName      first field name to use when finding distinct objects.
     * @param remainingFieldNames remaining field names when determining all unique combinations of field values.
     * @throws IllegalArgumentException if field names is empty or {@code null}, does not exist,
     *                                  is an unsupported type, or points to a linked field.
     */
//     * @throws IllegalStateException    if distinct field names were already defined.
    fun distinct(property: String, vararg extraProperties: String): RealmQuery<E>
    fun limit(results: Int): RealmQuery<E>

    // FIXME Is there a better way to force the constraints here. Restricting RealmQuery<E> to
    //  RealmObjects would help, but would prevent this class from being used by primitive queries.
    //  We need to investigate what other SDK's do here
    fun first(): RealmSingleQuery<E>

    fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T>
    fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T>

    /**
     * Calculates the average value for a given [property] and returns the result an instance of the
     * class specified by [type] or throws an [IllegalArgumentException] if the type doesn't match
     * the `property` or it is other than a [Number]. The `type` parameter supports all Realm numerals. If no values are present
     * for the given `property` the query will return `null`.
     *
     * If the provided `type` is something other than [Double] the computation might result in
     * precision loss under certain circumstances. For example, when calculating the average of the
     * integers `1` and `2` the output will be the [Int] resulting from calling
     * `averageValue.roundToInt()` and not `1.5`.
     *
     * If precision loss is not desired please use [average] with the required `property` and
     * ignore the `type` parameter - in which case the average will be returned in the form of a
     * [Double] - or use it providing `Double::class` as the `type` parameter.
     *
     * @param property the property on which the result will be computed.
     * @param type the type of the property.
     * @return the average value for the given property represented as a [Double].
     * @throws [IllegalArgumentException] if the property doesn't match the type or the property
     * is not a [Number].
     */
    fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<T>

    /**
     * Calculates the average value for a given [property] and returns a [Double] or throws an
     * [IllegalArgumentException] if the specified `property` cannot be converted to a [Double]. If
     * no values are present for the given `property` the query will return `null`.
     *
     * This function returns the average with the same precision provided by the native database.
     *
     * @param property the property on which the result will be computed.
     * @return the average value for the given property represented as a [Double].
     * @throws [IllegalArgumentException] if the property is not a [Number].
     */
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
