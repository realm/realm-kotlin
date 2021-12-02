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
import io.realm.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlin.reflect.KClass

@Suppress("SpreadOperator")
internal class RealmQueryImpl<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    private val descriptors: List<QueryDescriptor> = listOf(),
    private val composedQueryPointer: NativePointer? = null,
    private val filter: String,
    private vararg val args: Any?
) : RealmQuery<E>, Thawable<BaseResults<E>> {

    private val queryPointer: NativePointer = when {
        composedQueryPointer != null -> composedQueryPointer
        else -> parseQuery()
    }

    private val resultsPointer: NativePointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer)
    }

    constructor(
        composedQueryPointer: NativePointer?,
        queryImpl: RealmQueryImpl<E>
    ) : this(composedQueryPointer, queryImpl.descriptors, queryImpl)

    constructor(
        composedQueryPointer: NativePointer?,
        descriptors: List<QueryDescriptor>,
        queryImpl: RealmQueryImpl<E>
    ) : this(
        queryImpl.realmReference,
        queryImpl.clazz,
        queryImpl.mediator,
        descriptors,
        composedQueryPointer,
        queryImpl.filter,
        *queryImpl.args
    )

    override fun find(): RealmResults<E> =
        ElementResults(realmReference, resultsPointer, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        val appendedQuery = tryCatchCoreException {
            RealmInterop.realm_query_append_query(queryPointer, filter, *arguments)
        }
        return RealmQueryImpl(appendedQuery, this)
    }

    override fun sort(property: String, sortOrder: QuerySort): RealmQuery<E> =
        query("TRUEPREDICATE SORT($property ${sortOrder.name})")

    override fun sort(
        propertyAndSortOrder: Pair<String, QuerySort>,
        vararg additionalPropertiesAndOrders: Pair<String, QuerySort>
    ): RealmQuery<E> {
        val updatedDescriptors = (descriptors + QueryDescriptor.Sort(
            propertyAndSortOrder,
            *additionalPropertiesAndOrders
        ))
        return RealmQueryImpl(composedQueryPointer, updatedDescriptors, this)
    }

    override fun distinct(property: String, vararg extraProperties: String): RealmQuery<E> {
        val stringBuilder = StringBuilder().append("TRUEPREDICATE LIMIT($property")
        extraProperties.forEach { extraProperty ->
            stringBuilder.append(", $extraProperty")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun limit(limit: Int): RealmQuery<E> = query("TRUEPREDICATE LIMIT($limit)")

    override fun first(): RealmSingleQuery<E> =
        RealmSingleQueryImpl(realmReference, queryPointer, clazz, mediator)

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

//    override fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<T> =
//        GenericAggregatorQuery(
//            realmReference,
//            queryPointer,
//            mediator,
//            clazz,
//            property,
//            type,
//            AggregatorQueryType.AVERAGE
//        )
//
//    override fun average(property: String): RealmScalarQuery<Double> =
//        average(property, Double::class)

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator)

    override fun thaw(liveRealm: RealmReference): BaseResults<E> {
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }

    override fun asFlow(): Flow<RealmResults<E>> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }

    private fun parseQuery(): NativePointer = tryCatchCoreException(filter) {
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            clazz.simpleName!!,
            addQueryDescriptors(filter),
            *args
        )
    }

    private fun tryCatchCoreException(
        filter: String? = null,
        block: () -> NativePointer
    ): NativePointer = try {
        block.invoke()
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreInvalidQueryStringException ->
                IllegalArgumentException("Wrong query string: ${exception.message}")
            is RealmCoreInvalidQueryException ->
                IllegalArgumentException("Wrong query field provided or malformed syntax for query '$filter': ${exception.message}")
            is RealmCoreIndexOutOfBoundsException ->
                IllegalArgumentException("Have you specified all parameters for query '$filter'?: ${exception.message}")
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid syntax for query '$filter': ${exception.message}",
                    exception
                )
        }
    }

    private fun addQueryDescriptors(query: String): String {
        val stringBuilder = StringBuilder(query)
        descriptors.forEach { descriptor ->
            when (descriptor) {
                is QueryDescriptor.Sort -> buildSortDescriptor(stringBuilder, descriptor)
                is QueryDescriptor.Distinct -> buildDistinctDescriptor(stringBuilder, descriptor)
                is QueryDescriptor.Limit -> stringBuilder.append(" LIMIT(${descriptor.limit})")
            }
        }

        return stringBuilder.toString()
    }

    private fun buildSortDescriptor(
        stringBuilder: StringBuilder,
        descriptor: QueryDescriptor.Sort
    ) {
        stringBuilder.append(" SORT(")

        // Append initial sort
        val (firstProperty, firstSort) = descriptor.propertyAndSort
        stringBuilder.append("${escapeFieldName(firstProperty)} ${firstSort.name}")

        // Append potential additional sort descriptors
        descriptor.additionalPropertiesAndOrders.forEach { (property, order) ->
            stringBuilder.append(", ${escapeFieldName(property)} ${order.name}")
        }

        stringBuilder.append(")")
    }

    private fun buildDistinctDescriptor(
        stringBuilder: StringBuilder,
        descriptor: QueryDescriptor.Distinct
    ) {
        stringBuilder.append(" DISTINCT(")

        // Append initial distinct
        stringBuilder.append(escapeFieldName(descriptor.property))

        // Append potential additional distinct descriptors
        descriptor.additionalProperties.forEach { property ->
            stringBuilder.append(", ${escapeFieldName(property)}")
        }

        stringBuilder.append(")")
    }

    private fun escapeFieldName(fieldName: String?): String? = fieldName?.replace(" ", "\\ ")
}

internal sealed class QueryDescriptor {
    internal class Distinct(
        val property: String,
        vararg val additionalProperties: String
    ) : QueryDescriptor()

    internal class Sort(
        val propertyAndSort: Pair<String, QuerySort>,
        vararg val additionalPropertiesAndOrders: Pair<String, QuerySort>
    ) : QueryDescriptor()

    internal class Limit(val limit: Int) : QueryDescriptor()
}
