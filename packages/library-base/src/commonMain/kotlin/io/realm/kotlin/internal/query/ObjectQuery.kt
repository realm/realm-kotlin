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

package io.realm.kotlin.internal.query

import io.realm.kotlin.internal.InternalDeleteable
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Notifiable
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.RealmValueArgumentConverter.convertToQueryArgs
import io.realm.kotlin.internal.asInternalDeleteable
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.interop.RealmResultsPointer
import io.realm.kotlin.internal.interop.inputScope
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarNullableQuery
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.query.RealmSingleQuery
import io.realm.kotlin.query.Sort
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

@Suppress("SpreadOperator", "LongParameterList")
internal class ObjectQuery<E : BaseRealmObject> constructor(
    internal val realmReference: RealmReference,
    private val classKey: ClassKey,
    internal val clazz: KClass<E>,
    private val mediator: Mediator,
    internal val queryPointer: RealmQueryPointer,
) : RealmQuery<E>, InternalDeleteable, Observable<RealmResultsImpl<E>, ResultsChange<E>> {

    private val resultsPointer: RealmResultsPointer by lazy {
        RealmInterop.realm_query_find_all(queryPointer)
    }

    private val classMetadata: ClassMetadata? = realmReference.schemaMetadata[clazz.simpleName!!]

    internal constructor(
        realmReference: RealmReference,
        key: ClassKey,
        clazz: KClass<E>,
        mediator: Mediator,
        filter: String,
        args: Array<out Any?>
    ) : this(
        realmReference,
        key,
        clazz,
        mediator,
        parseQuery(realmReference, key, filter, args),
    )

    internal constructor(
        composedQueryPointer: RealmQueryPointer,
        objectQuery: ObjectQuery<E>
    ) : this(
        objectQuery.realmReference,
        objectQuery.classKey,
        objectQuery.clazz,
        objectQuery.mediator,
        composedQueryPointer,
    )

    override fun find(): RealmResults<E> =
        RealmResultsImpl(realmReference, resultsPointer, classKey, clazz, mediator)

    override fun query(filter: String, vararg arguments: Any?): RealmQuery<E> =
        inputScope {
            val appendedQuery = RealmInterop.realm_query_append_query(
                queryPointer,
                filter,
                convertToQueryArgs(arguments)
            )
            ObjectQuery(appendedQuery, this@ObjectQuery)
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
            classMetadata!!.getOrThrow(property),
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
            classMetadata!!.getOrThrow(property),
            type,
            AggregatorQueryType.MAX
        )

    override fun <T : Any> sum(property: String, type: KClass<T>): RealmScalarQuery<T> =
        SumQuery(
            realmReference,
            queryPointer,
            mediator,
            classKey,
            clazz,
            classMetadata!!.getOrThrow(property),
            type
        )

    override fun count(): RealmScalarQuery<Long> =
        CountQuery(realmReference, queryPointer, mediator, classKey, clazz)

    override fun notifiable(): Notifiable<RealmResultsImpl<E>, ResultsChange<E>> =
        QueryResultNotifiable(resultsPointer, classKey, clazz, mediator)

    override fun asFlow(): Flow<ResultsChange<E>> {
        return realmReference.owner
            .registerObserver(this)
    }

    override fun delete() {
        // TODO C-API doesn't implement realm_query_delete_all so just fetch the result and delete
        //  that
        find().asInternalDeleteable().delete()
    }

    override fun description(): String {
        return RealmInterop.realm_query_get_description(queryPointer)
    }

    companion object {
        private fun parseQuery(
            realmReference: RealmReference,
            classKey: ClassKey,
            filter: String,
            args: Array<out Any?>
        ): RealmQueryPointer = inputScope {
            val queryArgs = convertToQueryArgs(args)

            try {
                RealmInterop.realm_query_parse(realmReference.dbPointer, classKey, filter, queryArgs)
            } catch (e: IndexOutOfBoundsException) {
                throw IllegalArgumentException(e.message, e.cause)
            }
        }
    }
}
