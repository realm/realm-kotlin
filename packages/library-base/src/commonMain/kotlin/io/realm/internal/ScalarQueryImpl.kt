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

import io.realm.RealmQuery
import io.realm.RealmScalarQuery
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
 * This query produces the **average** of the values of a given [property] and returns its value as
 * an instance of the class specified by [type]. `type` supports all Realm numerals. If no values
 * are present for the given `property` the query will return `null`.
 *
 * If the provided `type` is something other than [Double] the computation might result in precision
 * loss under certain circumstances. For example, when calculating the average of the integers `1`
 * and `2` the output will be the [Int] resulting from calling `computedNativeAverage.roundToInt()`
 * and not `1.5`.
 *
 * If precision loss is not desired please use [RealmQuery.average] with the required `property` and
 * ignoring the `type` parameter - in which case the average will be returned in the form of a
 * [Double] - or use it providing `Double::class` as the `type` parameter.
 */
internal class AverageGenericQuery<T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator,
    private val clazz: KClass<*>,
    private val property: String,
    private val type: KClass<T>
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
        val colKey =
            RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
        try {
            val realmResultsAverage =
                RealmInterop.realm_results_average<Double?>(resultsPointer, colKey.key)

            // TODO Expand to support other numeric types, e.g. Decimal128
            return when (type) {
                Int::class -> realmResultsAverage?.roundToInt()
                Long::class -> realmResultsAverage?.roundToLong()
                Float::class -> realmResultsAverage?.toFloat()
                Double::class -> realmResultsAverage
                else -> throw IllegalArgumentException("Invalid numeric type for '$property', it is not a '${type.simpleName}'.")
            } as T?
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
