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
import io.realm.internal.ScalarResults
import io.realm.internal.Thawable
import io.realm.internal.genericRealmCoreExceptionHandler
import io.realm.internal.interop.ColumnKey
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreLogicException
import io.realm.internal.interop.RealmInterop
import io.realm.query.RealmScalarQuery
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlin.reflect.KClass

internal abstract class BaseScalarQuery<T : Any> constructor(
    protected val realmReference: RealmReference,
    protected val queryPointer: NativePointer,
    protected val mediator: Mediator
) : RealmScalarQuery<T>, Thawable<BaseResults<T>> {

    abstract val valueChangeManager: ValueChangeManager<T>

    abstract fun getScalarClass(): KClass<T>
    abstract fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?>

    override fun thaw(liveRealm: RealmReference): BaseResults<T> {
        val liveDbPointer = liveRealm.dbPointer
        val queryResults = RealmInterop.realm_query_find_all(queryPointer)
        val liveResultPtr = RealmInterop.realm_results_resolve_in(queryResults, liveDbPointer)
        return ScalarResults(liveRealm, liveResultPtr, getScalarClass(), mediator)
    }

    override fun asFlow(): Flow<T?> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .queryMapper()
    }

    protected fun getPropertyKey(clazz: KClass<*>, property: String): ColumnKey =
        RealmInterop.realm_get_col_key(realmReference.dbPointer, clazz.simpleName!!, property)
}

internal class CountQuery constructor(
    realmReference: RealmReference,
    queryPointer: NativePointer,
    mediator: Mediator
) : BaseScalarQuery<Long>(realmReference, queryPointer, mediator) {

    override val valueChangeManager = ValueChangeManager<Long>()

    override fun find(): Long = RealmInterop.realm_query_count(queryPointer)

    override fun getScalarClass(): KClass<Long> = Long::class

    override fun Flow<BaseResults<Long>?>.queryMapper(): Flow<Long> = this.map {
        requireNotNull(it).size.toLong()
    }.filter { latestCount ->
        valueChangeManager.shouldEmitValue(latestCount)
    }
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

    override val valueChangeManager: ValueChangeManager<T> = ValueChangeManager()

    override fun find(): T? = findInternal(queryPointer)

    override fun getScalarClass(): KClass<T> = type

    override fun Flow<BaseResults<T>?>.queryMapper(): Flow<T?> = this.map {
        it?.let { results ->
            findFromResults(results.nativePointer)
        }
    }.filter { latestValue: T? ->
        valueChangeManager.shouldEmitValue(latestValue)
    }

    private fun findInternal(queryPointer: NativePointer): T? =
        findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    private fun findFromResults(resultsPointer: NativePointer): T? {
        try {
            val colKey = getPropertyKey(clazz, property).key
            return computeAggregatedValue(resultsPointer, colKey)
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

/**
 * Convenience class to encapsulate the logic behind whether repeated elements should be emitted by
 * [RealmScalarQuery.asFlow]. The [firstUpdate] and [previousValue] properties need to be
 * [AtomicRef]s due to Kotlin Native's memory model.
 */
internal class ValueChangeManager<T> {

    private val firstUpdate: AtomicBoolean = atomic(true)
    private val previousValue: AtomicRef<T?> = atomic(null)

    fun shouldEmitValue(latestValue: T?): Boolean {
        return if (firstUpdate.value) {
            println("---> first update, value: $latestValue")
            firstUpdate.value = false
            previousValue.value = latestValue
            true
        } else {
            if (previousValue.value == latestValue) {
                println("---> value did NOT change!")
                false
            } else {
                println("---> value DID change - previous: ${previousValue.value}, latest: $latestValue")
                previousValue.value = latestValue
                true
            }
        }
    }
}

internal enum class AggregatorQueryType {
    MIN, MAX, SUM
}
