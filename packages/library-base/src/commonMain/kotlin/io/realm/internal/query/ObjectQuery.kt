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
import io.realm.internal.BaseResults
import io.realm.internal.ElementResults
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.Thawable
import io.realm.internal.genericRealmCoreExceptionHandler
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.internal.interop.RealmCoreInvalidQueryException
import io.realm.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.internal.interop.RealmInterop
import io.realm.query.RealmQuery
import io.realm.query.RealmScalarQuery
import io.realm.query.RealmSingleQuery
import io.realm.query.Sort
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("SpreadOperator")
internal class ObjectQuery<E : RealmObject> constructor(
    private val realmReference: RealmReference,
    private val clazz: KClass<E>,
    private val mediator: Mediator,
    composedQueryPointer: NativePointer? = null,
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
        objectQuery: ObjectQuery<E>
    ) : this(
        objectQuery.realmReference,
        objectQuery.clazz,
        objectQuery.mediator,
        composedQueryPointer,
        objectQuery.filter,
        *objectQuery.args
    )

    override fun find(): RealmResults<E> =
        ElementResults(realmReference, resultsPointer, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> {
        val appendedQuery = tryCatchCoreException {
            RealmInterop.realm_query_append_query(queryPointer, filter, *arguments)
        }
        return ObjectQuery(appendedQuery, this)
    }

    override fun sort(property: String, sortOrder: Sort): RealmQuery<E> =
        query("TRUEPREDICATE SORT($property ${sortOrder.name})")

    override fun sort(
        propertyAndSortOrder: Pair<String, Sort>,
        vararg additionalPropertiesAndOrders: Pair<String, Sort>
    ): RealmQuery<E> {
        val stringBuilder =
            StringBuilder().append("TRUEPREDICATE SORT(${propertyAndSortOrder.first} ${propertyAndSortOrder.second}")
        additionalPropertiesAndOrders.forEach { extraPropertyAndOrder ->
            stringBuilder.append(", ${extraPropertyAndOrder.first} ${extraPropertyAndOrder.second}")
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
        SingleQuery(realmReference, queryPointer, clazz, mediator)

    override fun <T : Any> min(property: String, type: KClass<T>): RealmScalarQuery<T> =
        AggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.MIN
        )

    override fun <T : Any> max(property: String, type: KClass<T>): RealmScalarQuery<T> =
        AggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.MAX
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> =
        AggregatorQuery(
            realmReference,
            queryPointer,
            mediator,
            clazz,
            property,
            type,
            AggregatorQueryType.SUM
        )

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator)

    override fun thaw(liveRealm: RealmReference): BaseResults<E> =
        thawResults(liveRealm, resultsPointer, clazz, mediator)

    override fun asFlow(): Flow<RealmResults<E>> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
    }

    private fun parseQuery(): NativePointer = tryCatchCoreException(filter) {
        RealmInterop.realm_query_parse(
            realmReference.dbPointer,
            clazz.simpleName!!,
            filter,
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
}
