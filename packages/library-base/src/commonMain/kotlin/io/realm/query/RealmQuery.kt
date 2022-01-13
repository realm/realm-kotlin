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
 */

package io.realm.query

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmInstant
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.Flowable
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * A `RealmQuery` encapsulates a query on a [Realm], a [RealmResults] or a [RealmList] instance
 * using the `Builder` pattern. The query is executed using either [find] or subscribing to the
 * [Flow] returned by [asFlow].
 *
 * A [Realm] is unordered, which means that there is no guarantee that a query will return the
 * objects in the order they where inserted. Use the [sort] functions if a specific order is
 * required.
 *
 * Results are obtained quickly most of the times when using [find]. However, launching heavy
 * queries from the UI thread may result in a drop of frames or even ANRs. If you want to prevent
 * these behaviors, you can use [asFlow] and collect the results asynchronously.
 *
 * @param T the class of the objects to be queried.
 */
interface RealmQuery<T : RealmObject> : RealmElementQuery<T> {

    /**
     * Appends the query represented by [filter] to an existing query using a logical `AND`.
     *
     * @param filter the Realm Query Language predicate to append.
     * @param arguments Realm values for the predicate.
     */
    fun query(filter: String, vararg arguments: Any?): RealmQuery<T>

    /**
     * Sorts the query result by the specific property name according to [sortOrder], which is
     * [Sort.ASCENDING] by default.
     *
     * Sorting is currently limited to character sets in 'Latin Basic', 'Latin Supplement',
     * 'Latin Extended A', and 'Latin Extended B' (UTF-8 range 0-591). For other character sets
     * sorting will have no effect.
     *
     * @param property the property name to sort by.
     * @throws [IllegalArgumentException] if the property name does not exist.
     */
    fun sort(property: String, sortOrder: Sort = Sort.ASCENDING): RealmQuery<T>

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
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): RealmQuery<T>

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
    fun distinct(property: String, vararg extraProperties: String): RealmQuery<T>

    /**
     * Limits the number of objects returned in case the query matched more objects.
     *
     * Note that when using this method in combination with [sort] and [distinct] they will be
     * executed in the order they where added which can affect the end result.
     *
     * @param limit a limit that is greater than or equal to 1.
     * @throws IllegalArgumentException if the provided [limit] is less than 1.
     */
    fun limit(limit: Int): RealmQuery<T>

    /**
     * Returns a query that finds the first object that fulfills the query conditions.
     */
    fun first(): RealmSingleQuery<T>

    /**
     * Finds the minimum value of a property.
     *
     * A reified version of this method is also available as an extension function,
     * `query.min<YourClass>(...)`. Import `io.realm.query.min` to access it.
     *
     * @param property the property on which to find the minimum value. Only [Number] and
     * [RealmInstant] properties are supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return a [RealmScalarQuery] returning the minimum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the minimum value is returned. When
     * determining the minimum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if
     * [type] cannot be used to represent the [property].
     */
    // TODO update doc when ObjectId and Decimal128 are added
    fun <T : Any> min(property: String, type: KClass<T>): RealmScalarNullableQuery<T>

    /**
     * Finds the maximum value of a property.
     *
     * A reified version of this method is also available as an extension function,
     * `query.max<YourClass>(...)`. Import `io.realm.query.max` to access it.
     *
     * @param property the property on which to find the maximum value. Only [Number] properties are
     * supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return a [RealmScalarQuery] returning the maximum value for the given [property] represented
     * as a [T]. If no objects exist or they all have `null` as the value for the given property,
     * `null` will be returned by the query. Otherwise, the maximum value is returned. When
     * determining the maximum value, objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if
     * [type] cannot be used to represent the [property].
     */
    // TODO update doc when ObjectId and Decimal128 are added
    fun <T : Any> max(property: String, type: KClass<T>): RealmScalarNullableQuery<T>

    /**
     * Calculates the sum of the given [property].
     *
     * If the aggregated result of the [property] does not fit into the specified [type] the result
     * will overflow following Kotlin's semantics for said type. For example, if the property
     * `floor` is a `Byte` and the specified type is also `Byte`, e.g.
     * `query.sum("floor", Short::class)`, the result will overflow for values greater than
     * [Byte.MAX_VALUE]. It is possible to circumvent this limitation by specifying a type which is
     * less likely to overflow in the query, e.g. `query.sum("floor", Int::class)`. In this case the
     * aggregated value will be an `Int`.
     *
     * A reified version of this method is also available as an extension function,
     * `query.sum<YourClass>(...)`. Import `io.realm.query.sum` to access it.
     *
     * @param property the property to sum. Only [Number] properties are supported.
     * @param type the type of the resulting aggregated value, which may or may not coincide with
     * the type of the property itself.
     * @return the sum of fields of the matching objects. If no objects exist or they all have
     * `null` as the value for the given property, `0` will be returned. When computing the sum,
     * objects with `null` values are ignored.
     * @throws IllegalArgumentException if the [property] is not a [Number] or a [Char], or if it is
     * a [RealmInstant], or if the [type] cannot be used to represent the [property].
     */
    fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T>

    /**
     * Returns a [RealmScalarQuery] that counts the number of objects that fulfill the query
     * conditions.
     *
     * @return a [RealmScalarQuery] that counts the number of objects for a given query.
     */
    fun count(): RealmScalarQuery<Long>

    // TODO add 'get_description': https://github.com/realm/realm-core/issues/5106
}

/**
 * Query returning [RealmResults].
 */
interface RealmElementQuery<T : RealmObject> : Flowable<RealmResults<T>> {

    /**
     * Finds all objects that fulfill the query conditions and returns them in a blocking fashion.
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * results asynchronously using [asFlow] instead.
     *
     * @return a [RealmResults] instance containing matching objects. If no objects match the
     * condition, an instance with zero objects is returned.
     */
    fun find(): RealmResults<T>

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
     * **It is not allowed to call [asFlow] on queries generated from a [MutableRealm].**
     *
     * @return a flow representing changes to the [RealmResults] resulting from running this query.
     */
    override fun asFlow(): Flow<RealmResults<T>>
}

/**
 * Query returning a single [RealmObject].
 */
interface RealmSingleQuery<T> : Flowable<T> {

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
    fun find(): T?

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
    override fun asFlow(): Flow<T?>
}

/**
 * Queries that return scalar values. This type of query is used to more accurately represent the
 * results provided by some query operations, e.g. [RealmQuery.count] or [RealmQuery.sum].
 */
interface RealmScalarQuery<T> : Flowable<T> {
    /**
     * Returns the value of a scalar query as a [T] in a blocking fashion. The result may be of a
     * different type depending on the type of query:
     *
     * - `[count]` returns [Long]
     * - `[sum]` returns the `type` specified in the call to said function
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * values asynchronously using [asFlow] instead.
     *
     * @return a [T] containing the result of the scalar query.
     */
    fun find(): T

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
    override fun asFlow(): Flow<T>
}

/**
 * Queries that return scalar, nullable values. This type of query is used to more accurately
 * represent the results provided by some query operations, e.g. [RealmQuery.min] or
 * [RealmQuery.max].
 */
interface RealmScalarNullableQuery<T> : Flowable<T> {
    /**
     * Returns the value of a scalar query as a [T] in a blocking fashion. The result may be `null`
     * if no objects are present.
     *
     * Launching heavy queries from the UI thread may result in a drop of frames or even ANRs. **We
     * do not recommend doing so.** If you want to prevent these behaviors you can obtain the
     * values asynchronously using [asFlow] instead.
     *
     * @return a [T] containing the result of the scalar query or `null` depending on the query
     * being executed.
     */
    fun find(): T?

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
    override fun asFlow(): Flow<T?>
}

/**
 * This enum describes the sorting order used in Realm queries.
 *
 * @see [RealmQuery.sort]
 */
enum class Sort {
    ASCENDING,
    DESCENDING
}

/**
 * Similar to [RealmQuery.min] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.min(property: String): RealmScalarNullableQuery<T> =
    min(property, T::class)

/**
 * Similar to [RealmQuery.max] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.max(property: String): RealmScalarNullableQuery<T> =
    max(property, T::class)

/**
 * Similar to [RealmQuery.sum] but the type parameter is automatically inferred.
 */
inline fun <reified T : Any> RealmQuery<*>.sum(property: String): RealmScalarQuery<T> =
    sum(property, T::class)

/**
 * Similar to [RealmQuery.find] but it receives a [block] in which the [RealmResults] from the query
 * are provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
fun <T : RealmObject, R> RealmQuery<T>.find(block: (RealmResults<T>) -> R): R = find().let(block)

/**
 * Similar to [RealmScalarQuery.find] but it receives a [block] in which the scalar result from the
 * query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
fun <T, R> RealmScalarQuery<T>.find(block: (T) -> R): R = find().let(block)

/**
 * Similar to [RealmScalarNullableQuery.find] but it receives a [block] in which the scalar result
 * from the query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
fun <T, R> RealmScalarNullableQuery<T>.find(block: (T?) -> R): R = find().let(block)

/**
 * Similar to [RealmSingleQuery.find] but it receives a [block] in which the [RealmObject] from the
 * query is provided.
 *
 * @param T the type of the query
 * @param R the type returned by [block]
 * @return whatever [block] returns
 */
fun <T, R> RealmSingleQuery<T>.find(block: (T?) -> R): R = find().let(block)
