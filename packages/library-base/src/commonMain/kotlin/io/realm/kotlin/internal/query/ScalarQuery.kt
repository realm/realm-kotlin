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

import io.realm.kotlin.internal.CoreExceptionConverter
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.Thawable
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmCoreException
import io.realm.kotlin.internal.interop.RealmCoreLogicException
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.interop.RealmResultsPointer
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarNullableQuery
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

/**
 * Shared logic for scalar queries.
 *
 * Observe that this class needs the [E] representing a [BaseRealmObject] to avoid having to split
 * [RealmResults] in object and scalar implementations and to be able to observe changes to the
 * scalar values for the query - more concretely to allow returning a [RealmResultsImpl] object by
 * [thaw]ing it, which in turn comes from processing said results with `Flow.map` on the resulting
 * [Flow].
 */
internal abstract class BaseScalarQuery<E : BaseRealmObject> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: RealmQueryPointer,
    protected val mediator: Mediator,
    protected val classKey: ClassKey,
    protected val clazz: KClass<E>
) : Thawable<Observable<RealmResultsImpl<E>, ResultsChange<E>>> {

    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> {
        val liveDbPointer = liveRealm.dbPointer
        val queryResults = RealmInterop.realm_query_find_all(queryPointer)
        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
        return RealmResultsImpl(liveRealm, liveResultPtr, classKey, clazz, mediator)
    }

    // TODO OPTIMIZE Maybe add classKey->ClassMetadata map to realmReference.schemaMetadata
    //  so that we can get the key directly from a lookup
    protected fun getPropertyKey(property: String): PropertyKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, classKey, property)
}

/**
 * Returns how many objects there are. The result is devliered as a [Long].
 */
internal class CountQuery<E : BaseRealmObject> constructor(
    realmReference: RealmReference,
    queryPointer: RealmQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz),
    RealmScalarQuery<Long> {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun asFlow(): Flow<Long> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map {
                it.list.size.toLong()
            }.distinctUntilChanged()
    }
}

/**
 * Query for either [RealmQuery.min] or [RealmQuery.max]. The result will be `null` if a particular
 * table is empty.
 */
@Suppress("LongParameterList")
internal class MinMaxQuery<E : BaseRealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: RealmQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>,
    private val property: String,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz),
    RealmScalarNullableQuery<T> {

    override fun find(): T? = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    override fun asFlow(): Flow<T?> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map { findFromResults((it.list as RealmResultsImpl<*>).nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: RealmResultsPointer): T? = try {
        computeAggregatedValue(resultsPointer, getPropertyKey(property))
    } catch (exception: Throwable) {
        throw CoreExceptionConverter.convertToPublicException(
            exception,
            "Invalid query formulation: ${exception.message}",
        ) { coreException: RealmCoreException ->
            when (coreException) {
                is RealmCoreLogicException ->
                    IllegalArgumentException(
                        "Invalid query formulation: ${exception.message}",
                        exception
                    )
                else -> null
            }
        }
    }

    @Suppress("ComplexMethod")
    private fun computeAggregatedValue(
        resultsPointer: RealmResultsPointer,
        propertyKey: PropertyKey
    ): T? = getterScope {
        val transport = when (queryType) {
            AggregatorQueryType.MIN ->
                RealmInterop.realm_results_min(allocRealmValueT(), resultsPointer, propertyKey)
            AggregatorQueryType.MAX ->
                RealmInterop.realm_results_max(allocRealmValueT(), resultsPointer, propertyKey)
            AggregatorQueryType.SUM ->
                throw IllegalArgumentException("Use SumQuery instead.")
        }

        @Suppress("UNCHECKED_CAST")
        return when (transport) {
            null -> null
            else -> when (type) {
                Int::class -> transport.getLong().toInt()
                Short::class -> transport.getLong().toShort()
                Long::class -> transport.getLong()
                Float::class -> transport.getFloat()
                Double::class -> transport.getDouble()
                Byte::class -> transport.getLong().toByte()
                Char::class -> transport.getLong().toInt().toChar()
                RealmInstant::class -> transport.getTimestamp().let { timestamp ->
                    RealmInstant.from(timestamp.seconds, timestamp.nanoSeconds)
                }
                else -> throw IllegalArgumentException("Invalid property type for '$property', only Int, Long, Short, Byte, Double, Float and RealmInstant (except for 'SUM') properties can be aggregated.")
            }
        } as T?
    }
}

/**
 * Computes the sum of all entries for a given property. The result is always non-nullable.
 */
@Suppress("LongParameterList")
internal class SumQuery<E : BaseRealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: RealmQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>,
    private val property: String,
    private val type: KClass<T>
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz),
    RealmScalarQuery<T> {

    override fun find(): T = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    override fun asFlow(): Flow<T> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map { findFromResults((it.list as RealmResultsImpl<*>).nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: RealmResultsPointer): T = try {
        computeAggregatedValue(resultsPointer, getPropertyKey(property))
    } catch (exception: Throwable) {
        throw CoreExceptionConverter.convertToPublicException(
            exception,
            "Invalid query formulation: ${exception.message}",
        ) { coreException: RealmCoreException ->
            when (coreException) {
                is RealmCoreLogicException ->
                    IllegalArgumentException(
                        "Invalid query formulation: ${exception.message}",
                        exception
                    )
                else -> null
            }
        }
    }

    private fun computeAggregatedValue(
        resultsPointer: RealmResultsPointer,
        propertyKey: PropertyKey
    ): T = getterScope {
        val transport = RealmInterop.realm_results_sum(allocRealmValueT(), resultsPointer, propertyKey)

        // When doing a SUM on RLM_TYPE_INT property the output is a Long
        // but for RLM_TYPE_DOUBLE and RLM_TYPE_FLOAT the output is Double
        @Suppress("UNCHECKED_CAST")
        return when (type) {
            Int::class -> transport.getLong().toInt()
            Short::class -> transport.getLong().toShort()
            Long::class -> transport.getLong()
            Float::class -> transport.getDouble().toFloat()
            Double::class -> transport.getDouble()
            Byte::class -> transport.getLong().toByte()
            Char::class -> transport.getLong().toInt().toChar()
            else -> throw IllegalArgumentException("Invalid property type for '$property', only Int, Long, Short, Double, Float properties can be used with SUM.")
        } as T
    }
}

// TODO Public due to being used in QueryTests
public enum class AggregatorQueryType {
    MIN, MAX, SUM
}
