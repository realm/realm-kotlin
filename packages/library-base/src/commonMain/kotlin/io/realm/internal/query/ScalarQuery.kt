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

import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.internal.Mediator
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.RealmReference
import io.realm.internal.RealmResultsImpl
import io.realm.internal.Thawable
import io.realm.internal.genericRealmCoreExceptionHandler
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.PropertyKey
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreLogicException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.Timestamp
import io.realm.query.RealmQuery
import io.realm.query.RealmScalarNullableQuery
import io.realm.query.RealmScalarQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

/**
 * Shared logic for scalar queries.
 *
 * Observe that this class needs the [E] representing a [RealmObject] to avoid having to split
 * [RealmResults] in object and scalar implementations and to be able to observe changes to the
 * scalar values for the query - more concretely to allow returning a [RealmResultsImpl] object by
 * [thaw]ing it, which in turn comes from processing said results with `Flow.map` on the resulting
 * [Flow].
 */
internal abstract class BaseScalarQuery<E : RealmObject> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator,
    protected val className: String,
    protected val clazz: KClass<E>
) : Thawable<RealmResultsImpl<E>> {

    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> {
        val liveDbPointer = liveRealm.dbPointer
        val queryResults = RealmInterop.realm_query_find_all(queryPointer)
        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
        return RealmResultsImpl(liveRealm, liveResultPtr, className, clazz, mediator)
    }

    // TODO OPTIMIZE Can't we just retrieve this once from the schema metadata
    protected fun getPropertyKey(className: String, property: String): PropertyKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, className, property)
}

/**
 * Returns how many objects there are. The result is devliered as a [Long].
 */
internal class CountQuery<E : RealmObject> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    className: String,
    clazz: KClass<E>
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, className, clazz), RealmScalarQuery<Long>, Thawable<RealmResultsImpl<E>> {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun asFlow(): Flow<Long> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map {
                it.size.toLong()
            }.distinctUntilChanged()
    }
}

/**
 * Query for either [RealmQuery.min] or [RealmQuery.max]. The result will be `null` if a particular
 * table is empty.
 */
@Suppress("LongParameterList")
internal class MinMaxQuery<E : RealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    className: String,
    clazz: KClass<E>,
    private val property: String,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, className, clazz), RealmScalarNullableQuery<T>, Thawable<RealmResultsImpl<E>> {

    override fun find(): T? = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    override fun asFlow(): Flow<T?> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map { findFromResults(it.nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: NativePointer): T? = try {
        // TODO OPTIMIZE
        val colKey = getPropertyKey(className, property).key
        computeAggregatedValue(resultsPointer, colKey)
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreLogicException ->
                IllegalArgumentException(
                    "Invalid query formulation: ${exception.message}",
                    exception
                )
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid query formulation: ${exception.message}",
                    exception
                )
        }
    }

    @Suppress("ComplexMethod")
    private fun computeAggregatedValue(resultsPointer: NativePointer, colKey: Long): T? {
        val result: T? = when (queryType) {
            AggregatorQueryType.MIN ->
                RealmInterop.realm_results_min(resultsPointer, colKey)
            AggregatorQueryType.MAX ->
                RealmInterop.realm_results_max(resultsPointer, colKey)
            AggregatorQueryType.SUM ->
                throw IllegalArgumentException("Use SumQuery instead.")
        }
        // TODO Expand to support other numeric types, e.g. Decimal128
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            null -> null
            is Timestamp -> RealmInstant.fromEpochSeconds(result.seconds, result.nanoSeconds)
            is Number -> when (type) {
                Int::class -> result.toInt()
                Short::class -> result.toShort()
                Long::class -> result.toLong()
                Float::class -> result.toFloat()
                Double::class -> result.toDouble()
                Byte::class -> result.toByte()
                Char::class -> result.toChar()
                else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}' or cannot be represented by it.")
            }
            else -> throw IllegalArgumentException("Invalid property type for '$property', only Int, Long, Short, Double, Float and RealmInstant (except for 'SUM') properties can be aggregated.")
        } as T?
    }
}

/**
 * Computes the sum of all entries for a given property. The result is always non-nullable.
 */
internal class SumQuery<E : RealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    className: String,
    clazz: KClass<E>,
    private val property: String,
    private val type: KClass<T>
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, className, clazz), RealmScalarQuery<T>, Thawable<RealmResultsImpl<E>> {

    override fun find(): T = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    override fun asFlow(): Flow<T> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map { findFromResults(it.nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: NativePointer): T = try {
        val colKey = getPropertyKey(className, property).key
        computeAggregatedValue(resultsPointer, colKey)
    } catch (exception: RealmCoreException) {
        throw when (exception) {
            is RealmCoreLogicException ->
                IllegalArgumentException(
                    "Invalid query formulation: ${exception.message}",
                    exception
                )
            else ->
                genericRealmCoreExceptionHandler(
                    "Invalid query formulation: ${exception.message}",
                    exception
                )
        }
    }

    private fun computeAggregatedValue(resultsPointer: NativePointer, colKey: Long): T {
        val result: T = RealmInterop.realm_results_sum(resultsPointer, colKey)
        // TODO Expand to support other numeric types, e.g. Decimal128
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            is Number -> when (type) {
                Int::class -> result.toInt()
                Short::class -> result.toShort()
                Long::class -> result.toLong()
                Float::class -> result.toFloat()
                Double::class -> result.toDouble()
                Byte::class -> result.toByte()
                Char::class -> result.toChar()
                else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}' or cannot be represented by it.")
            }
            else -> throw IllegalArgumentException("Invalid property type for '$property', only Int, Long, Short, Double, Float properties can be used with SUM.")
        } as T
    }
}

enum class AggregatorQueryType {
    MIN, MAX, SUM
}
