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
import io.realm.query.RealmScalarQuery
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal enum class AggregatorQueryType {
    MIN, MAX, SUM
}

/**
 * Shared logic for scalar queries.
 *
 * Observe that this class needs the [E] representing a [RealmObject] to avoid having to split
 * [RealmResults] in object and scalar implementations and to be able to observe changes to the
 * scalar values for the query - more concretely to allow returning a [RealmResultsImpl] object by
 * [thaw], which in turn comes from processing said results with [queryMapper].
 */
internal abstract class BaseScalarQuery<E : RealmObject, T : Any> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator
) : RealmScalarQuery<T>, Thawable<RealmResultsImpl<E>> {

    abstract fun Flow<RealmResultsImpl<E>?>.queryMapper(): Flow<T?>
    abstract fun discardRepeated(latestValue: T?): Boolean

    override fun thaw(liveRealm: RealmReference): RealmResultsImpl<E> = TODO()

    override fun asFlow(): Flow<T?> = TODO()

    protected fun getPropertyKey(clazz: KClass<*>, property: String): PropertyKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
}

internal class CountQuery<E : RealmObject> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator
) : BaseScalarQuery<E, Long>(realmReference, queryPointer, mediator) {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun Flow<RealmResultsImpl<E>?>.queryMapper(): Flow<Long?> = TODO()

    override fun discardRepeated(latestValue: Long?): Boolean = TODO()
}

@Suppress("LongParameterList")
internal class AggregatorQuery<E : RealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    private val clazz: KClass<*>,
    private val property: String,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<E, T>(realmReference, queryPointer, mediator) {

    override fun find(): T? = findInternal(queryPointer)

    override fun Flow<RealmResultsImpl<E>?>.queryMapper(): Flow<T?> = TODO()

    override fun discardRepeated(latestValue: T?): Boolean = TODO()

    private fun findInternal(queryPointer: NativePointer): T? =
        findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    private fun findFromResults(resultsPointer: NativePointer): T? = try {
        val colKey = getPropertyKey(clazz, property).key
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
                RealmInterop.realm_results_sum(resultsPointer, colKey)
        }
        // TODO Expand to support other numeric types, e.g. Decimal128
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
                else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}'.")
            }
            else -> throw IllegalArgumentException("Invalid property type for '$property', only Int, Long, Short, Double, Float and RealmInstant (except for 'SUM') properties can be aggregated.")
        } as T?
    }
}
