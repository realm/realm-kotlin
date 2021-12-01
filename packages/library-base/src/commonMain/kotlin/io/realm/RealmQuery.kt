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
 * Query returning [RealmResults].
 */
interface RealmElementQuery<E> : Flowable<RealmResults<E>> {

    /**
     * Finds all objects that fulfill the query conditions and returns them in a blocking fashion.
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * results asynchronously using [asFlow] instead.
     *
     * @return a [RealmResults] instance containing objects. If no objects match the condition, an
     * instance with zero objects is returned.
     */
    fun find(): RealmResults<E>

    /**
     * Finds all objects that fulfill the query conditions and returns them asynchronously as a
     * [Flow].
     *
     * If there is any changes to the objects represented by the query backing the [RealmResults],
     * the flow will emit the updated results. The flow will continue running indefinitely until
     * canceled.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the [RealmResults] resulting from running this query.
     */
    override fun asFlow(): Flow<RealmResults<E>>
}

/**
 * Query returning a single [RealmObject].
 * 
 * TODO: The interaction with primitive queries might be a bit awkward
 * TODO: answer to above from C-API:
 *  Note: This function can only produce objects, not values. Use the
 *       `realm_results_t` returned by `realm_query_find_all()` to retrieve
 *        values from a list of primitive values.
 */
interface RealmSingleQuery<E> : Flowable<E> {

    /**
     * Finds the first object that fulfills the query conditions and returns it in a blocking
     * fashion.
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * object asynchronously using [asFlow] instead.
     *
     * @return a [RealmObject] instance or `null` if no object matches the condition.
     */
    fun find(): E?

    /**
     * Finds the first object that fulfills the query conditions and returns it asynchronously as a
     * [Flow].
     *
     * If there is any changes to the object represented by the query, the flow will emit the
     * updated object. The flow will continue running indefinitely until canceled.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the [RealmObject] resulting from running this query.
     */
    override fun asFlow(): Flow<E?>
}

/**
 * Queries that return scalar values. This type of query is used to more accurately represent the
 * results provided by some query operations, e.g. [RealmQuery.count] or [RealmQuery.min]..
 */
interface RealmScalarQuery<E> : Flowable<E> {
    /**
     * Returns the value of a scalar query as an [E] in a blocking fashion. The result may be `null`
     * depending on the type of scalar query:
     *
     * - `[min]`, `[max]` return [E] or `null` if no objects are present
     * - `[count]` returns [Long]
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * values asynchronously using [asFlow] instead.
     *
     * @return an [E] containing the result of the scalar query or `null` depending on the query
     * being executed.
     */
    fun find(): E?

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
     */
    override fun asFlow(): Flow<E?>
}

/**
 * A `RealmQuery` encapsulates a query on a [Realm] or a [RealmResults] instance using the `Builder`
 * pattern. The query is executed using either [find] or [asFlow].
 *
 * The input to many of the query functions take a field name as [String]. Note that this is not
 * type safe. If a [RealmObject] class is refactored, care has to be taken to not break any queries.
 *
 * A [Realm] is unordered, which means that there is no guarantee that a query will return the
 * objects in the order they where inserted. Use the [sort] functions if a specific order is
 * required.
 *
 * Results are obtained quickly most of the times when using [find]. However, launching heavy
 * queries from the UI thread may result in a drop of frames or even ANRs. If you want to prevent
 * these behaviors, you can use [asFlow] to retrieve results asynchronously.
 *
 * @param E the class of the objects to be queried.
 */
// TODO update docs when Decimal128 and RealmAny are added
interface RealmQuery<E> : RealmElementQuery<E> {

    /**
     * Appends the query represented by [filter] to an existing query using a logical `AND`.
     *
     * @param filter the Realm Query Language predicate to append.
     * @param arguments Realm values for the predicate.
     */
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
     * @param property first field name to use when finding distinct objects.
     * @param extraProperties remaining property names when determining all unique combinations of
     * field values.
     * @throws IllegalArgumentException if [property] does not exist, is an unsupported type, or
     * points to a linked field.
     */
    // TODO "points to a linked field" doesn't apply yet
    fun distinct(property: String, vararg extraProperties: String): RealmQuery<E>

    /**
     * Limits the number of objects returned in case the query matched more objects.
     *
     * Note that when using this method in combination with [sort] and [distinct] they will be
     * executed in the order they where added which can affect the end result.
     *
     * @param limit a limit that is greater than or equal to 1.
     * @throws IllegalArgumentException if the provided [limit] is less than 1.
     */
    fun limit(limit: Int): RealmQuery<E>

    /**
     * Returns a query that finds the first object that fulfills the query conditions.
     */
    fun first(): RealmSingleQuery<E>

    /**
     * Finds the minimum value of a property.
     *
     * @param property the property on which to find the minimum value. Only [Number] properties are
     * supported.
     * @param type the type of the property.
     * @return a [RealmScalarQuery] returning the minimum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the minimum value is returned. When
     * determining the minimum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the field is not a [Number].
     */
    fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T>

    /**
     * Finds the maximum value of a property.
     *
     * @param property the property on which to find the maximum value. Only [Number] properties are
     * supported.
     * @param type the type of the property.
     * @return a [RealmScalarQuery] returning the maximum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the maximum value is returned. When
     * determining the maximum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the field is not a [Number].
     */
    fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T>

    /**
     * Calculates the sum of a given property.
     *
     * @param property the property to sum. Only [Number] properties are supported.
     * @param type the type of the property.
     * @return the sum of fields of the matching objects. If no objects exist or they all have
     * `null` as the value for the given property, `0` will be returned. When computing the sum,
     * objects with `null` values are ignored.
     * @throws IllegalArgumentException if the field is not a [Number] type.
     */
    fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T>

//    /**
//     * Calculates the average value for a given [property] and returns the result as an instance of
//     * the class specified by [T] or throws an [IllegalArgumentException] if the `property` type
//     * doesn't match [T] or it is other than a [Number]. The `type` parameter supports all Realm
//     * numerals. If no values are present for the given `property` the query will return `null`.
//     *
//     * If the provided `type` is something other than [Double] the computation might result in
//     * precision loss under certain circumstances. For example, when calculating the average of the
//     * integers `1` and `2` the output will be the [Int] resulting from calling
//     * `averageValue.roundToInt()` and not `1.5`.
//     *
//     * If precision loss is not desired please use [average] with the required `property` and
//     * ignore the `type` parameter - in which case the average will be returned in the form of a
//     * [Double] - or use it providing `Double::class` as the `type` parameter.
//     *
//     * @param property the property on which the result will be computed.
//     * @param type the type of the property.
//     * @return a [RealmScalarQuery] returning the average value for the given [property] represented
//     * as a [T].
//     * @throws [IllegalArgumentException] if the property doesn't match [T] or the property is not
//     * a [Number].
//     */
//    fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<T>
//
//    /**
//     * Calculates the average value for a given [property] and returns a [Double] or throws an
//     * [IllegalArgumentException] if the specified `property` cannot be converted to a [Double]. If
//     * no values are present for the given `property` the query will return `null`.
//     *
//     * This function returns the average with the same precision provided by the native database.
//     *
//     * @param property the property on which the result will be computed.
//     * @return a [RealmScalarQuery] returning the average value for the given property represented
//     * as a [Double].
//     * @throws [IllegalArgumentException] if the property is not a [Number].
//     */
//    fun average(property: String): RealmScalarQuery<Double>

    /**
     * Returns a [RealmScalarQuery] that counts the number of objects that fulfill the query
     * conditions.
     *
     * @return a [RealmScalarQuery] that counts the number of objects for a given query.
     */
    fun count(): RealmScalarQuery<Long>
}

/**
 * Similar to [RealmQuery.min] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.min(property: String): RealmScalarQuery<T> =
    min(property, T::class)

/**
 * Similar to [RealmQuery.max] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.max(property: String): RealmScalarQuery<T> =
    max(property, T::class)

/**
 * Similar to [RealmQuery.sum] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.sum(property: String): RealmScalarQuery<T> =
    sum(property, T::class)

/**
 * Similar to [RealmQuery.find] but it receives a block in which the [RealmResults] from the query
 * are provided.
 */
fun <T> RealmQuery<T>.find(block: (RealmResults<T>) -> Unit): RealmResults<T> = find().let {
    block.invoke(it)
    it
}

/**
 * Similar to [RealmScalarQuery.find] but it receives a block in which the scalar result from the
 * query is provided.
 */
fun <T> RealmScalarQuery<T>.find(block: (T?) -> Unit): T? = find().let {
    block.invoke(it)
    it
}

/**
 * Similar to [RealmSingleQuery.find] but it receives a block in which the [RealmObject] from the
 * query is provided.
 */
fun <T> RealmSingleQuery<T>.find(block: (T?) -> Unit): T? = find().let {
    block.invoke(it)
    it
}

/**
 * This enum describes the sorting order used in Realm queries.
 *
 * @see [RealmQuery.sort]
 */
enum class QuerySort {
    ASCENDING,
    DESCENDING
}
