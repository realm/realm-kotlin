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
import io.realm.kotlin.internal.CoreExceptionConverter
import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.NotificationFlowable
import io.realm.kotlin.internal.Observable
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.RealmResultsImpl
import io.realm.kotlin.internal.RealmValueConverter
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmCoreException
import io.realm.kotlin.internal.interop.RealmCoreLogicException
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_max
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_min
import io.realm.kotlin.internal.interop.RealmInterop.realm_results_sum
import io.realm.kotlin.internal.interop.RealmQueryPointer
import io.realm.kotlin.internal.interop.RealmResultsPointer
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.interop.getterScope
import io.realm.kotlin.internal.primitiveTypeConverters
import io.realm.kotlin.internal.realmAnyConverter
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
import org.mongodb.kbson.Decimal128
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
) : NotificationFlowable<RealmResultsImpl<E>, ResultsChange<E>> {

    override fun observable(): Observable<RealmResultsImpl<E>, ResultsChange<E>> =
        QueryResultObservable(
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
internal interface TypeBoundQuery {
    val propertyMetadata: PropertyMetadata
    val converter: RealmValueConverter<*>
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
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery, RealmScalarNullableQuery<T> {

    override val converter: RealmValueConverter<*> = when (propertyMetadata.type) {
        PropertyType.RLM_PROPERTY_TYPE_INT -> primitiveTypeConverters[Long::class]!!
        PropertyType.RLM_PROPERTY_TYPE_FLOAT -> primitiveTypeConverters[Float::class]!!
        PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> primitiveTypeConverters[Double::class]!!
        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128 -> primitiveTypeConverters[Decimal128::class]!!
        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> primitiveTypeConverters[RealmInstant::class]!!
        PropertyType.RLM_PROPERTY_TYPE_MIXED -> realmAnyConverter(mediator, realmReference)
        else -> throw IllegalArgumentException("Conversion not possible between '$type' and '${type.simpleName}'.")
    }

    // Validate we can coerce the type correctly
    init {
        queryTypeValidator(propertyMetadata, type, validateTimestamp = true)
    }

    override fun find(): T? = findFromResults(RealmInterop.realm_query_find_all(queryPointer))

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
        updatedRealmReference: RealmReference? = null
    ): T? = try {
        getterScope {
            val transport = when (queryType) {
                AggregatorQueryType.MIN -> realm_results_min(resultsPointer, propertyMetadata.key)
                AggregatorQueryType.MAX -> realm_results_max(resultsPointer, propertyMetadata.key)
                AggregatorQueryType.SUM -> throw IllegalArgumentException("Use SumQuery instead.")
            }

            @Suppress("UNCHECKED_CAST")
            when (type) {
                // Asynchronous aggregations require a converter with an updated realm reference
                RealmAny::class -> when (updatedRealmReference) {
                    null -> converter
                    else -> realmAnyConverter(mediator, updatedRealmReference)
                }.realmValueToPublic(transport)
                else -> coerceType(converter, propertyMetadata.name, type, transport)
            } as T?
        }
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
) : BaseScalarQuery<E>(realmReference, queryPointer, mediator, classKey, clazz), TypeBoundQuery, RealmScalarQuery<T> {

    // RealmAny SUMs are computed as Decimal128 and Float fields return Double
    override val converter: RealmValueConverter<*> = when (propertyMetadata.type) {
        PropertyType.RLM_PROPERTY_TYPE_INT -> primitiveTypeConverters[Long::class]!!
        PropertyType.RLM_PROPERTY_TYPE_FLOAT -> primitiveTypeConverters[Double::class]!!
        PropertyType.RLM_PROPERTY_TYPE_DOUBLE -> primitiveTypeConverters[Double::class]!!
        PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP -> primitiveTypeConverters[RealmInstant::class]!!
        PropertyType.RLM_PROPERTY_TYPE_DECIMAL128,
        PropertyType.RLM_PROPERTY_TYPE_MIXED -> primitiveTypeConverters[Decimal128::class]!!
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

    private fun findFromResults(resultsPointer: RealmResultsPointer): T = try {
        getterScope {
            val transport = realm_results_sum(resultsPointer, propertyMetadata.key)

            when (type) {
                RealmAny::class -> converter.realmValueToPublic(transport)
                else -> coerceType(converter, propertyMetadata.name, type, transport)
            }
        } as T
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

/**
 * Converts a value in the form of "storage type", i.e. type of the transport object produced by the
 * C-API, to a user-specified type in the query, i.e. "coerced type".
 */
@Suppress("ComplexMethod")
// TODO optimize: try to move this to query construction
private fun <T : Any> coerceType(
    converter: RealmValueConverter<*>,
    propertyName: String,
    coercedType: KClass<T>,
    transport: RealmValue
): T? {
    return when (transport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        // Core INT can be coerced to any numeric as long as Kotlin supports it
        ValueType.RLM_TYPE_INT -> {
            val storageTypeValue = converter.realmValueToPublic(transport) as Long?
            when (coercedType) {
                Short::class -> storageTypeValue?.toShort()
                Int::class -> storageTypeValue?.toInt()
                Byte::class -> storageTypeValue?.toByte()
                Char::class -> storageTypeValue?.toInt()?.toChar()
                Long::class -> storageTypeValue
                Double::class -> storageTypeValue?.toDouble()
                Float::class -> storageTypeValue?.toFloat()
                else -> throw IllegalArgumentException("Cannot coerce type of property '$propertyName' to '${coercedType.simpleName}'.")
            }
        }
        // Core FLOAT can be coerced to any numeric as long as Kotlin supports it
        ValueType.RLM_TYPE_FLOAT -> {
            val storageTypeValue = converter.realmValueToPublic(transport) as Float?
            when (coercedType) {
                Short::class -> storageTypeValue?.toInt()?.toShort()
                Int::class -> storageTypeValue?.toInt()
                Byte::class -> storageTypeValue?.toInt()?.toByte()
                Char::class -> storageTypeValue?.toInt()?.toChar()
                Long::class -> storageTypeValue?.toInt()?.toLong()
                Double::class -> storageTypeValue?.toDouble()
                Float::class -> storageTypeValue
                else -> throw IllegalArgumentException("Cannot coerce type of property '$$propertyName' to '${coercedType.simpleName}'.")
            }
        }
        // Core DOUBLE can be coerced to any numeric as long as Kotlin supports it
        ValueType.RLM_TYPE_DOUBLE -> {
            val storageTypeValue = converter.realmValueToPublic(transport) as Double?
            when (coercedType) {
                Short::class -> storageTypeValue?.toInt()?.toShort()
                Int::class -> storageTypeValue?.toInt()
                Byte::class -> storageTypeValue?.toInt()?.toByte()
                Char::class -> storageTypeValue?.toInt()?.toChar()
                Long::class -> storageTypeValue?.toInt()?.toLong()
                Double::class -> storageTypeValue
                Float::class -> storageTypeValue?.toFloat()
                else -> throw IllegalArgumentException("Cannot coerce type of property '$propertyName' to '${coercedType.simpleName}'.")
            }
        }
        // Core TIMESTAMP cannot be coerced to any type other than RealmInstant
        ValueType.RLM_TYPE_DECIMAL128,
        ValueType.RLM_TYPE_TIMESTAMP -> {
            converter.realmValueToPublic(transport)
        }
        else -> throw IllegalArgumentException("Cannot coerce type of property '$propertyName' to '${coercedType.simpleName}'.")
    } as T?
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
