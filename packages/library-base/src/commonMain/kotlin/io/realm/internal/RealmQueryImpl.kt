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
internal class RealmQueryImpl<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    private val queryDescriptor: List<QueryDescriptor> = listOf(),
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
        TODO("Not yet implemented")
    }

    override fun sort(property: String, sortOrder: QuerySort): RealmQuery<E> {
        val updatedDescriptors = queryDescriptor + QueryDescriptor.Sort(property to sortOrder)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun sort(
        propertyAndSortOrder: Pair<String, QuerySort>,
        vararg additionalPropertiesAndOrders: Pair<String, QuerySort>
    ): RealmQuery<E> {
        val updatedDescriptors = (queryDescriptor + QueryDescriptor.Sort(propertyAndSortOrder))
            .let { sortDescriptors ->
                sortDescriptors + additionalPropertiesAndOrders.map { (property, order) ->
                    QueryDescriptor.Sort(property to order)
                }
            }
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun distinct(property: String): RealmQuery<E> {
        val updatedDescriptors = queryDescriptor + QueryDescriptor.Distinct(property)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun limit(results: Int): RealmQuery<E> {
        val updatedDescriptors = queryDescriptor + QueryDescriptor.Limit(results)
        return RealmQueryImpl(realmReference, clazz, mediator, updatedDescriptors, query, *args)
    }

    override fun first(): RealmSingleQuery<E> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> {
        TODO("Not yet implemented")
    }

//    override fun average(property: String, type: KClass<*>): RealmScalarQuery<*> {
//        TODO("Not yet implemented")
//    }

    override fun <T : Any> average(property: String, type: KClass<T>): RealmScalarQuery<Double> {
        if (!type.isNumber()) {
            throw IllegalArgumentException("Average can only be executed on numeral properties.")
        }
        return AverageQuery(realmReference, queryPointer, mediator, clazz, property)
    }

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator)

    override fun thaw(liveRealm: RealmReference): BaseResults<E> {
        val liveResults = RealmInterop.realm_results_resolve_in(resultsPointer, liveRealm.dbPointer)
        return ElementResults(liveRealm, liveResults, clazz, mediator)
    }

    override fun asFlow(): Flow<RealmResults<E>> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }

    // TODO Expand to support other numeric types, e.g. Decimal128
    private fun KClass<*>.isNumber(): Boolean = this.simpleName == Int::class.simpleName ||
        this.simpleName == Double::class.simpleName ||
        this.simpleName == Flow::class.simpleName ||
        this.simpleName == Long::class.simpleName

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
                genericRealmCoreExceptionHandler("Invalid syntax for query '$query': ${exception.message}", exception)
        }
    }

    private fun addQueryDescriptors(query: String): String = StringBuilder(query).apply {
        queryDescriptor.forEach { descriptor ->
            when (descriptor) {
                is QueryDescriptor.Sort -> {
                    // Append initial sort descriptor
                    val (firstProperty, firstSort) = descriptor.propertyAndSort
                    this.append(" SORT($firstProperty ${firstSort.name})")

                    // Append potential additional sort descriptors
                    descriptor.additionalPropertiesAndOrders.forEach { (property, order) ->
                        this.append(" SORT($property ${order.name})")
                    }
                }
                is QueryDescriptor.Distinct -> this.append(" DISTINCT(${descriptor.property})")
                is QueryDescriptor.Limit -> this.append(" LIMIT(${descriptor.results})")
            }
        }
    }.toString()
}

/**
 * TODO : query
 */
internal sealed class QueryDescriptor {
    internal class Sort(
        val propertyAndSort: Pair<String, QuerySort>,
        vararg val additionalPropertiesAndOrders: Pair<String, QuerySort>
    ) : QueryDescriptor()

    internal class Distinct(val property: String) : QueryDescriptor()
    internal class Limit(val results: Int) : QueryDescriptor()
}
