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

package io.realm.internal.query

import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.Flowable
import io.realm.internal.InternalDeleteable
import io.realm.internal.Mediator
import io.realm.internal.Observable
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.Thawable
import io.realm.internal.asInternalDeleteable
import io.realm.internal.genericRealmCoreExceptionHandler
import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.RealmQueryPointer
import io.realm.internal.interop.RealmResultsPointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.internal.interop.RealmCoreInvalidQueryException
import io.realm.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.internal.interop.RealmInterop
import io.realm.notifications.ResultsChange
import io.realm.query.RealmQuery
import io.realm.query.RealmScalarNullableQuery
import io.realm.query.RealmScalarQuery
import io.realm.query.RealmSingleQuery
import io.realm.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("SpreadOperator", "LongParameterList")
internal class ObjectQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val classKey: ClassKey,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    composedQueryPointer: RealmQueryPointer? = null,
    private val filter: String,
    private vararg val args: Any?
) : RealmQuery<E>, InternalDeleteable, Thawable<Observable<RealmResultsImpl<E>, ResultsChange<E>>>, Flowable<ResultsChange<E>> {

    private val queryPointer: RealmQueryPointer = when {
        composedQueryPointer != null -> composedQueryPointer
        else -> parseQuery()
    }

    private val resultsPointer: RealmResultsPointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer)
    }

    constructor(
        composedQueryPointer: RealmQueryPointer?,
        objectQuery: ObjectQuery<E>
    ) : this(
        objectQuery.realmReference,
        objectQuery.classKey,
        objectQuery.clazz,
        objectQuery.mediator,
        composedQueryPointer,
        objectQuery.filter,
        *objectQuery.args
    )

    override fun find(): RealmResults<E> =
        RealmResultsImpl(realmReference, resultsPointer, classKey, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        val appendedQuery = tryCatchCoreException {
            RealmInterop.realm_query_append_query(queryPointer, filter, *arguments)
        }
        return ObjectQuery(appendedQuery, this)
    }

    // TODO OPTIMIZE Descriptors are added using 'append_query', which requires an actual predicate.
    //  This might result into query strings like "TRUEPREDICATE AND TRUEPREDICATE SORT(...)". We
    //  should look into how to avoid this, perhaps by exposing a different function that internally
    //  ignores unnecessary default predicates.
    override fun sort(property: String, sortOrder: Sort): RealmQuery<E> =
        query("TRUEPREDICATE SORT($property ${sortOrder.name})")

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): RealmQuery<E> {
        val (property, order) = propertyAndSortOrder
        val stringBuilder = StringBuilder().append("TRUEPREDICATE SORT($property $order")
        additionalPropertiesAndOrders.forEach { (extraProperty, extraOrder) ->
            stringBuilder.append(", $extraProperty $extraOrder")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun distinct(property: String, vararg extraProperties: String): RealmQuery<E> {
        val stringBuilder = StringBuilder().append("TRUEPREDICATE DISTINCT($property")
        extraProperties.forEach { extraProperty ->
            stringBuilder.append(", $extraProperty")
        }
        stringBuilder.append(")")
        return query(stringBuilder.toString())
    }

    override fun limit(limit: Int): RealmQuery<E> = query("TRUEPREDICATE LIMIT($limit)")

    override fun first(): RealmSingleQuery<E> =
        SingleQuery(realmReference, queryPointer, classKey, clazz, mediator)

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarNullableQuery<T> =
        MinMaxQuery(
            realmReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            property,
            type,
            AggregatorQueryType.MIN
        )

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarNullableQuery<T> =
        MinMaxQuery(
            realmReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            property,
            type,
            AggregatorQueryType.MAX
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> =
        SumQuery(realmReference, queryPointer, mediator, classKey, clazz, property, type)

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator, classKey, clazz)

    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> =
        thawResults(liveRealm, resultsPointer, classKey, clazz, mediator)

    override fun asFlow(): Flow<ResultsChange<E>> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
    }

    override fun delete() {
        // TODO C-API doesn't implement realm_query_delete_all so just fetch the result and delete
        //  that
        find().asInternalDeleteable().delete()
    }

    private fun parseQuery(): RealmQueryPointer = tryCatchCoreException {
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            classKey,
            filter,
            *args
        )
    }

    private fun tryCatchCoreException(block: () -> RealmQueryPointer): RealmQueryPointer = try {
        block.invoke()
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreInvalidQueryStringException ->
                IllegalArgumentException("Wrong query string: ${exception.message}")
            is RealmCoreInvalidQueryException ->
                IllegalArgumentException("Wrong query field provided or malformed syntax in query: ${exception.message}")
            is RealmCoreIndexOutOfBoundsException ->
                IllegalArgumentException("Have you specified all parameters in your query?: ${exception.message}")
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid syntax in query: ${exception.message}",
                    exception
                )
        }
    }
}
