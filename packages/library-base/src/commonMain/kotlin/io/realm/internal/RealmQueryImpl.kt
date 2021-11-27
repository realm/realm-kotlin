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

package io.realm.internal

import io.realm.QuerySort
import io.realm.RealmObject
import io.realm.RealmQuery
import io.realm.RealmResults
import io.realm.RealmScalarQuery
import io.realm.RealmSingleQuery
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.internal.interop.RealmCoreInvalidQueryException
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

/**
 * TODO : query
 */
@Suppress("SpreadOperator")
internal class RealmQueryImpl<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    private val descriptors: List<QueryDescriptor> = listOf(),
    private val query: String,
    private vararg val args: Any?
) : RealmQuery<E>, Thawable<BaseResults<E>> {

    private val queryPointer: NativePointer = parseQuery()

    private val resultsPointer: NativePointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer)
    }

    override fun find(): RealmResults<E> =
        ElementResults(realmReference, resultsPointer, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        // TODO https://github.com/realm/realm-core/issues/5067
        TODO("Not yet implemented")
    }

    override fun sort(property: String, sortOrder: QuerySort): RealmQuery<E> {
        val updatedDescriptors = descriptors + QueryDescriptor.Sort(property to sortOrder)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun sort(
        propertyAndSortOrder: Pair<String, QuerySort>,
        vararg additionalPropertiesAndOrders: Pair<String, QuerySort>
    ): RealmQuery<E> {
        val updatedDescriptors = (descriptors + QueryDescriptor.Sort(propertyAndSortOrder))
            .let { sortDescriptors ->
                sortDescriptors + additionalPropertiesAndOrders.map { (property, order) ->
                    QueryDescriptor.Sort(property to order)
                }
            }
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun distinct(property: String): RealmQuery<E> {
        val updatedDescriptors = descriptors + QueryDescriptor.Distinct(property)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun limit(results: Int): RealmQuery<E> {
        val updatedDescriptors = descriptors + QueryDescriptor.Limit(results)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun first(): RealmSingleQuery<E> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T> =
        GenericAggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.MIN
        )

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T> =
        GenericAggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.MAX
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> =
        GenericAggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.SUM
        )

    /**
     * Calculates the average value for a given [property] and returns the result an instance of the
     * class specified by [type] or throws an [IllegalArgumentException] if the type doesn't match
     * the `property`. The `type` parameter supports all Realm numerals. If no values are present
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
     * @throws [IllegalArgumentException] if the property doesn't match the type.
     */
    override fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<T> =
        GenericAggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.AVERAGE
        )

    /**
     * Calculates the average value for a given [property] and returns a [Double] or throws an
     * [IllegalArgumentException] if the specified `property` cannot be converted to a [Double]. If
     * no values are present for the given `property` the query will return `null`.
     *
     * This function returns the average with the same precision provided by the native database.
     *
     * @param property the property on which the result will be computed.
     * @return the average value for the given property represented as a [Double].
     * @throws [IllegalArgumentException] if the property doesn't match the type.
     */
    override fun average(property: String): RealmScalarQuery<Double> =
        average(property, Double::class)

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator)

    override fun thaw(liveRealm: RealmReference): BaseResults<E> {
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }

    override fun asFlow(): Flow<RealmResults<E>> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }

    private fun parseQuery(): NativePointer = try {
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            clazz.simpleName!!,
            addQueryDescriptors(query),
            *args
        )
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreInvalidQueryException ->
                IllegalArgumentException("Wrong query field provided or malformed query syntax for query '$query': ${exception.message}")
            is RealmCoreIndexOutOfBoundsException ->
                IllegalArgumentException("Have you specified all parameters for query '$query'?: ${exception.message}")
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid syntax for query '$query': ${exception.message}",
                    exception
                )
        }
    }

    private fun addQueryDescriptors(query: String): String {
        val stringBuilder = StringBuilder(query)

        descriptors.forEach { descriptor ->
            when (descriptor) {
                is QueryDescriptor.Sort -> {
                    // Append initial sort descriptor
                    val (firstProperty, firstSort) = descriptor.propertyAndSort
                    stringBuilder.append(" SORT($firstProperty ${firstSort.name})")

                    // Append potential additional sort descriptors
                    descriptor.additionalPropertiesAndOrders.forEach { (property, order) ->
                        stringBuilder.append(" SORT($property ${order.name})")
                    }
                }
                is QueryDescriptor.Distinct -> stringBuilder.append(" DISTINCT(${descriptor.property})")
                is QueryDescriptor.Limit -> stringBuilder.append(" LIMIT(${descriptor.results})")
            }
        }

        return stringBuilder.toString()
    }
}

/**
 * TODO : query
 */
internal sealed class QueryDescriptor {
    internal class Distinct(val property: String) : QueryDescriptor()
    internal class Limit(val results: Int) : QueryDescriptor()
    internal class Sort(
        val propertyAndSort: Pair<String, QuerySort>,
        vararg val additionalPropertiesAndOrders: Pair<String, QuerySort>
    ) : QueryDescriptor()
}
