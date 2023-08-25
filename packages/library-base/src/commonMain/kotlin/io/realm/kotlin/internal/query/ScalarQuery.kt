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

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.internal.Decimal128Converter
import io.realm.kotlin.internal.DoubleConverter
import io.realm.kotlin.internal.FloatConverter
import io.realm.kotlin.internal.IntConverter
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.Notifiable
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmInstantConverter
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_max
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_min
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_sum
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.interop.RealmResultsPointer
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.realmValueToRealmAny
import io.realm.kotlin.internal.schema.PropertyMetadata
import io.realm.kotlin.notifications.ResultsChange
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.query.RealmScalarNullableQuery
import io.realm.kotlin.query.RealmScalarQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.mongodb.kbson.BsonDecimal128
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
) : Observable<RealmResultsImpl<E>, ResultsChange<E>> {

    override fun notifiable(): Notifiable<RealmResultsImpl<E>, ResultsChange<E>> =
        QueryResultNotifiable(
            RealmInterop.realm_query_find_all(queryPointer),
            classKey,
            clazz,
            mediator
        )
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
 * Type-bound query linked to a property. Unlike [CountQuery] this is executed at a table level
 * rather than at a column level.
 */
internal interface TypeBoundQuery<T> {
    val propertyMetadata: PropertyMetadata
    val converter: (RealmValue) -> T?
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
    override val propertyMetadata: PropertyMetadata,
    private val type: KClass<T>,
    private val queryType: AggregatorQueryType
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery<T>, RealmScalarNullableQuery<T> {

    override val converter: (RealmValue) -> T? = when(propertyMetadata.type) {
        PropertyType.RLM_PROPERTY_TYPE_INT -> { it -> IntConverter.fromRealmValue(it)?.let { coerceLong(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_FLOAT -> { it -> FloatConverter.fromRealmValue(it)?.let { coerceFloat(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> { it -> DoubleConverter.fromRealmValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> { it -> RealmInstantConverter.fromRealmValue(it) as T? }
        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128 -> { it -> Decimal128Converter.fromRealmValue(it) as T? }
        PropertyType.RLM_PROPERTY_TYPE_MIXED -> { it ->
            // Mixed fields rely on updated realmReference to resolve objects, so postpone
            // conversion until values are resolved to unity immediate and async results
            error("Mixed values should be aggregated elsewhere")
        }
        else -> throw IllegalArgumentException("Conversion not possible between '$type' and '${type.simpleName}'.")
    }

    // Validate we can coerce the type correctly
    init {
        queryTypeValidator(propertyMetadata, type, validateTimestamp = true)
    }

    override fun find(): T? = findFromResults(RealmInterop.realm_query_find_all(queryPointer), realmReference)

    override fun asFlow(): Flow<T?> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map {
                val realmResults = it.list as RealmResultsImpl<*>
                findFromResults(realmResults.nativePointer, realmResults.realm)
            }.distinctUntilChanged()
    }

    // When computing asynchronous aggregations we need to use a converter that has an updated
    // realm reference or else we risk failing at getting the latest version of objects
    // e.g. when computing MAX on a RealmAny property when the MAX value is a RealmObject
    private fun findFromResults(
        resultsPointer: RealmResultsPointer,
        realmReference: RealmReference
    ): T? = getterScope {
        val transport = when (queryType) {
            AggregatorQueryType.MIN -> realm_results_min(resultsPointer, propertyMetadata.key)
            AggregatorQueryType.MAX -> realm_results_max(resultsPointer, propertyMetadata.key)
            AggregatorQueryType.SUM -> throw IllegalArgumentException("Use SumQuery instead.")
        }

        @Suppress("UNCHECKED_CAST")
        when (type) {
            // Asynchronous aggregations require a converter with an updated realm reference
            RealmAny::class ->
                realmValueToRealmAny(
                    transport, null, mediator, realmReference, false, false,
                ) as T?
            else -> converter(transport)
        } as T?
    }
}

/**
 * Computes the sum of all entries for a given property. The result is always non-nullable.
 * Specialized versions for [TypedRealm]s and [DynamicRealm]s extend this class.
 */
@Suppress("LongParameterList")
internal class SumQuery<E : BaseRealmObject, T : Any> constructor(
    realmReference: RealmReference,
    queryPointer: RealmQueryPointer,
    mediator: Mediator,
    classKey: ClassKey,
    clazz: KClass<E>,
    override val propertyMetadata: PropertyMetadata,
    private val type: KClass<T>
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery<T>, RealmScalarQuery<T> {

    override val converter: (RealmValue) -> T? = when(propertyMetadata.type) {
        PropertyType.RLM_PROPERTY_TYPE_INT -> { it -> IntConverter.fromRealmValue(it)?.let { coerceLong(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_FLOAT -> { it -> DoubleConverter.fromRealmValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> { it -> DoubleConverter.fromRealmValue(it)?.let { coerceDouble(propertyMetadata.name, it, type) } as T? }
        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> { it -> RealmInstantConverter.fromRealmValue(it) as T? }
        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
        PropertyType.RLM_PROPERTY_TYPE_MIXED ->
            { it -> Decimal128Converter.fromRealmValue(it) as T? }
        else -> throw IllegalArgumentException("Conversion not possible between '$type' and '${type.simpleName}'.")
    }

    // Validate we can coerce the type correctly
    init {
        queryTypeValidator(propertyMetadata, type)
    }

    override fun find(): T = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

    override fun asFlow(): Flow<T> {
        realmReference.checkClosed()
        return realmReference.owner
            .registerObserver(this)
            .map { findFromResults((it.list as RealmResultsImpl<*>).nativePointer) }
            .distinctUntilChanged()
    }

    private fun findFromResults(resultsPointer: RealmResultsPointer): T = getterScope {
        converter(realm_results_sum(resultsPointer, propertyMetadata.key))
    } as T
}

/**
 * Validates the type coercion parameters for the query.
 */
private fun <T : Any> queryTypeValidator(
    propertyMetadata: PropertyMetadata,
    type: KClass<T>,
    validateTimestamp: Boolean = false
) {
    val fieldType: PropertyType = propertyMetadata.type
    if (fieldType == PropertyType.RLM_PROPERTY_TYPE_MIXED) {
        // RealmAny can only be coerced to RealmAny
        if (type != RealmAny::class) {
            throw IllegalArgumentException("RealmAny properties cannot be aggregated as '${type.simpleName}'. Use RealmAny as output type instead.")
        }
    } else if (fieldType == PropertyType.RLM_PROPERTY_TYPE_DECIMAL128) {
        // Decimal128 can only be coerced to Decimal128
        if (type != BsonDecimal128::class) {
            throw IllegalArgumentException("Decimal128 properties cannot be aggregated as '${type.simpleName}'. Use Decimal128 as output type instead.")
        }
    } else if (validateTimestamp && fieldType == PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP) {
        // Timestamps cannot be summed and cannot be coerced
        if (type != RealmInstant::class) {
            throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
        }
    } else if (type.isNumeric()) {
        // Numerics can be coerced to any other numeric type supported by Core (as long as Kotlin's type system allows it)
        if (fieldType != PropertyType.RLM_PROPERTY_TYPE_INT &&
            fieldType != PropertyType.RLM_PROPERTY_TYPE_FLOAT &&
            fieldType != PropertyType.RLM_PROPERTY_TYPE_DOUBLE
        ) {
            throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
        }
    } else {
        // Otherwise we are coercing two disallowed types
        throw IllegalArgumentException("Conversion not possible between '$fieldType' and '${type.simpleName}'.")
    }
}

internal fun coerceLong(propertyName: String, value: Long, coercedType: KClass<*>):Any {
    return when (coercedType) {
            Short::class -> value.toShort()
            Int::class -> value.toInt()
            Byte::class -> value.toByte()
            Char::class -> value.toInt().toChar()
            Long::class -> value
            Double::class -> value.toDouble()
            Float::class -> value.toFloat()
            else -> throw IllegalArgumentException("Cannot coerce type of property '$propertyName' to '${coercedType.simpleName}'.")
        }
}
internal fun coerceFloat(propertyName: String, value: Float, coercedType: KClass<*>): Any {
    return when (coercedType) {
        Short::class -> value.toInt().toShort()
        Int::class -> value.toInt()
        Byte::class -> value.toInt().toByte()
        Char::class -> value.toInt().toChar()
        Long::class -> value.toInt().toLong()
        Double::class -> value.toDouble()
        Float::class -> value
        else -> throw IllegalArgumentException("Cannot coerce type of property '$$propertyName' to '${coercedType.simpleName}'.")
    }
}
internal fun coerceDouble(propertyName: String, value: Double, coercedType: KClass<*>): Any {
    return when (coercedType) {
        Short::class -> value.toInt().toShort()
        Int::class -> value.toInt()
        Byte::class -> value.toInt().toByte()
        Char::class -> value.toInt().toChar()
        Long::class -> value.toInt().toLong()
        Double::class -> value
        Float::class -> value.toFloat()
        else -> throw IllegalArgumentException("Cannot coerce type of property '$$propertyName' to '${coercedType.simpleName}'.")
    }
}

private fun KClass<*>.isNumeric(): Boolean {
    return this == Short::class ||
        this == Int::class ||
        this == Byte::class ||
        this == Char::class ||
        this == Long::class ||
        this == Float::class ||
        this == Double::class
}

// Public due to being used in QueryTests
public enum class AggregatorQueryType {
    MIN, MAX, SUM
}
