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

import io.realm.internal.BaseResults
import io.realm.internal.Mediator
import io.realm.internal.RealmReference
import io.realm.internal.Thawable
import io.realm.internal.genericRealmCoreExceptionHandler
import io.realm.internal.interop.ColumnKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreLogicException
import io.realm.internal.interop.RealmInterop
import io.realm.query.RealmScalarQuery
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

internal enum class AggregatorQueryType {
    MIN, MAX, SUM
}

internal abstract class BaseScalarQuery<T : Any> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator
) : RealmScalarQuery<T>, Thawable<BaseResults<T>> {

    abstract fun getScalarClass(): KClass<T>
    abstract fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?>
    abstract fun discardRepeated(latestValue: T?): Boolean

    override fun thaw(liveRealm: RealmReference): BaseResults<T> = TODO()

    override fun asFlow(): Flow<T?> = TODO()

    protected fun getPropertyKey(clazz: KClass<*>, property: String): ColumnKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
}

internal class CountQuery constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator
) : BaseScalarQuery<Long>(realmReference, queryPointer, mediator) {

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun getScalarClass(): KClass<Long> = Long::class

    override fun Flow<BaseResults<Long>?>.queryMapper(): Flow<Long> = TODO()

    override fun discardRepeated(latestValue: Long?): Boolean = TODO()
}

@Suppress("LongParameterList")
internal class AggregatorQuery<T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    private val clazz: KClass<*>,
    private val property: String,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<T>(realmReference, queryPointer, mediator) {

    override fun find(): T? = findInternal(queryPointer)

    override fun getScalarClass(): KClass<T> = type

    override fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?> = TODO()

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

    private fun computeAggregatedValue(resultsPointer: NativePointer, colKey: Long): T? {
        val result: T? = when (queryType) {
            AggregatorQueryType.MIN ->
                RealmInterop.realm_results_min(resultsPointer, colKey)
            AggregatorQueryType.MAX ->
                RealmInterop.realm_results_max(resultsPointer, colKey)
            AggregatorQueryType.SUM ->
                RealmInterop.realm_results_sum(resultsPointer, colKey)
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
}
