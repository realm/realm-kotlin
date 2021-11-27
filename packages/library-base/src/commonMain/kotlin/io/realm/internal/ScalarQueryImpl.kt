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

import io.realm.RealmScalarQuery
import io.realm.internal.interop.ColumnKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreLogicException
import io.realm.internal.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.KClass

/**
 * TODO : query
 */
internal enum class AggregatorQueryType {
    MIN, MAX, SUM, AVERAGE
}

/**
 * TODO : query
 */
internal abstract class ScalarQueryImpl<T : Any> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator
) : RealmScalarQuery<T>, Thawable<BaseResults<T>> {

    abstract fun getScalarClass(): KClass<T>

    abstract fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?>

    override fun thaw(liveRealm: RealmReference): BaseResults<T> {
        val liveDbPointer = liveRealm.dbPointer
        val queryResults = RealmInterop.realm_query_find_all(queryPointer)
        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
        return ScalarResults(liveRealm, liveResultPtr, getScalarClass(), mediator)
    }

    override fun asFlow(): Flow<T?> = realmReference.owner
        .registerObserver(this)
        .onStart { realmReference.checkClosed() }
        .queryMapper()

    protected fun getPropertyKey(clazz: KClass<*>, property: String): ColumnKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
}

/**
 * TODO : query
 */
internal class CountQuery constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator
) : ScalarQueryImpl<Long>(realmReference, queryPointer, mediator) {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun getScalarClass(): KClass<Long> = Long::class

    override fun Flow<BaseResults<Long>?>.queryMapper(): Flow<Long> = this.map {
        requireNotNull(it).size.toLong()
    }
}

/**
 * TODO : query
 */
@Suppress("LongParameterList")
internal class GenericAggregatorQuery<T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    private val clazz: KClass<*>,
    private val property: String,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : ScalarQueryImpl<T>(realmReference, queryPointer, mediator) {

    override fun find(): T? = findInternal(queryPointer)

    override fun getScalarClass(): KClass<T> = type

    override fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?> = this.map {
        it?.let { results ->
            findFromResults(results.nativePointer)
        }
    }

    private fun findInternal(queryPointer: NativePointer): T? =
        findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    private fun findFromResults(resultsPointer: NativePointer): T? {
        return try {
            val colKey = getPropertyKey(clazz, property).key
            when (queryType) {
                AggregatorQueryType.AVERAGE ->
                    computeAverage(resultsPointer, colKey, property, type)
                else ->
                    computeOther(resultsPointer, colKey, property, type, queryType)
            }
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
    }
}

private fun <T : Any> computeOther(
    resultsPointer: NativePointer,
    colKey: Long,
    property: String,
    type: KClass<T>,
    queryType: AggregatorQueryType
): T? {
    val result: T? = when (queryType) {
        AggregatorQueryType.MIN ->
            RealmInterop.realm_results_min(resultsPointer, colKey)
        AggregatorQueryType.MAX ->
            RealmInterop.realm_results_max(resultsPointer, colKey)
        AggregatorQueryType.SUM ->
            RealmInterop.realm_results_sum(resultsPointer, colKey)
        AggregatorQueryType.AVERAGE ->
            throw UnsupportedOperationException("Wrong function, use 'computeAverage' instead.")
    }

    val numberResult: Number = result?.let { it as Number } ?: return null

    // TODO Expand to support other numeric types, e.g. Decimal128
    return when (type) {
        Int::class -> numberResult.toInt()
        Long::class -> numberResult.toLong()
        Float::class -> numberResult.toFloat()
        Double::class -> numberResult.toDouble()
        else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}'.")
    } as T?
}

// Hard to fold together with the standard case due to type signature shenanigans
private fun <T : Any> computeAverage(
    resultsPointer: NativePointer,
    colKey: Long,
    property: String,
    type: KClass<T>
): T? {
    val average =
        RealmInterop.realm_results_average<Double?>(resultsPointer, colKey) ?: return null

    // TODO Expand to support other numeric types, e.g. Decimal128
    return when (type) {
        Int::class -> average.roundToInt()
        Long::class -> average.roundToLong()
        Float::class -> average.toFloat()
        Double::class -> average
        else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}'.")
    } as T?
}
