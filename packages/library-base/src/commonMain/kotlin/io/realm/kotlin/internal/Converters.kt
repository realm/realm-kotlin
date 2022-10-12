/*
 * Copyright 2022 Realm Inc.
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

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.dynamic.DynamicMutableRealmObject
import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.internal.interop.Link
import io.realm.kotlin.internal.interop.ObjectIdWrapper
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.RealmValueTransport
import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.internal.interop.UUIDWrapper
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.platform.realmObjectCompanionOrNull
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import kotlin.native.concurrent.SharedImmutable
import kotlin.reflect.KClass

// cinterop -> SDK
public inline fun valueTransportToInt(valueTransport: RealmValueTransport): Int? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getInt()
            .also {
                println("---> valueTransportToInt: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Int>()
    }

public inline fun valueTransportToShort(valueTransport: RealmValueTransport): Short? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getShort()
            .also {
                println("---> valueTransportToShort: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Short>()
    }

public inline fun valueTransportToLong(valueTransport: RealmValueTransport): Long? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getLong()
            .also {
                println("---> valueTransportToLong: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Long>()
    }

public inline fun valueTransportToByte(valueTransport: RealmValueTransport): Byte? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getByte()
            .also {
                println("---> valueTransportToByte: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Byte>()
    }

public inline fun valueTransportToChar(valueTransport: RealmValueTransport): Char? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getChar()
            .also {
                println("---> valueTransportToChar: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Char>()
    }

public inline fun valueTransportToBoolean(valueTransport: RealmValueTransport): Boolean? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getBoolean()
            .also {
                println("---> valueTransportToBoolean: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Boolean>()
    }

public inline fun valueTransportToString(valueTransport: RealmValueTransport): String? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getString()
            .also {
                println("---> valueTransportToString: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<String>()
    }

public inline fun valueTransportToBinary(valueTransport: RealmValueTransport): ByteArray? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getByteArray()
            .also {
                println("---> valueTransportToBinary: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<ByteArray>()
    }

public inline fun valueTransportToInstant(valueTransport: RealmValueTransport): RealmInstant? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> RealmInstantImpl(valueTransport.getTimestamp())
            .also {
                println("---> valueTransportToInstant: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> RealmInstantImpl(valueTransport.get<Timestamp>())
    }

public inline fun valueTransportToFloat(valueTransport: RealmValueTransport): Float? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getFloat()
            .also {
                println("---> valueTransportToFloat: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Float>()
    }

public inline fun valueTransportToDouble(valueTransport: RealmValueTransport): Double? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> valueTransport.getDouble()
            .also {
                println("---> valueTransportToDouble: $it")
                valueTransport.free()
                println("---> --- freed native struct A")
            }
//        else -> valueTransport.get<Double>()
    }

public inline fun valueTransportToObjectId(valueTransport: RealmValueTransport): ObjectId? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> {
            println("---> valueTransportToObjectId")
            ObjectIdImpl(valueTransport.getObjectIdWrapper())
                .also {
                    println("---> --- freed native struct A")
                    valueTransport.free()
                }
        }
//        else -> ObjectIdImpl(valueTransport.get<ObjectIdWrapper>())
    }

public inline fun valueTransportToUUID(valueTransport: RealmValueTransport): RealmUUID? =
    when (valueTransport.getType()) {
        ValueType.RLM_TYPE_NULL -> null
        else -> {
            println("---> valueTransportToUUID")
            RealmUUIDImpl(valueTransport.getUUIDWrapper())
                .also {
                    valueTransport.free()
                    println("---> --- freed native struct A")
                }
        }
//        else -> RealmUUIDImpl(valueTransport.get<UUIDWrapper>())
    }

//public inline fun <reified T : Any> valueTransportToGeneric(
//    valueTransport: RealmValueTransport
//): T? {
//    val type = valueTransport.getType()
//
//    @Suppress("IMPLICIT_CAST_TO_ANY")
//    val result = when (type) {
//        ValueType.RLM_TYPE_NULL -> null
//        ValueType.RLM_TYPE_TIMESTAMP -> RealmInstantImpl(valueTransport.get<Timestamp>())
//        ValueType.RLM_TYPE_OBJECT_ID -> ObjectIdImpl(valueTransport.get<ObjectIdWrapper>())
//        ValueType.RLM_TYPE_UUID -> RealmUUIDImpl(valueTransport.get<UUIDWrapper>())
////        ValueType.RLM_TYPE_LINK -> TODO()
//        else -> valueTransport.get<T>()
//    } as T?
//    return result
//}

// SDK -> cinterop
public fun genericToValueTransport(value: Any?): RealmValueTransport {
    val result = when (value) {
        null -> RealmValueTransport.createNull()
        is Int -> RealmValueTransport(value)
        is Short -> RealmValueTransport(value)
        is Long -> RealmValueTransport(value)
        is Byte -> RealmValueTransport(value)
        is Char -> RealmValueTransport(value)
        is Boolean -> RealmValueTransport(value)
        is String -> RealmValueTransport(value)
        is ByteArray -> RealmValueTransport(value)
        is Timestamp -> RealmValueTransport(value)
        is Float -> RealmValueTransport(value)
        is Double -> RealmValueTransport(value)
        is ObjectIdWrapper -> RealmValueTransport(value)
        is UUIDWrapper -> RealmValueTransport(value)
        else -> throw IllegalArgumentException("Unsupported value for transport: $value")
    }
    return result
}

// This file contains all code for converting public API values into values passed to the C-API.
// This conversion is split into a two-step operation to:
// - Maximize code reuse of individual conversion steps to ensure consistency throughout the
//   compiler plugin injected code and the library
// - Accommodate future public (or internal default) type converters
// The two steps are:
// 1. Converting public user facing types to internal "storage types" which are library specific
//    Kotlin types mimicing the various underlying core types.
// 2. Converting from the "library storage types" into the C-API intepretable corresponding value
// The "C-API values" are passed in and out of the C-API as RealmValue that is just a `value class`-
// wrapper around `Any` that is converted into `realm_value_t` in the `cinterop` layer.

/**
 * Interface for overall conversion between public types and C-API input/output types. This is the
 * main abstraction of conversion used throughout the library.
 */
internal interface RealmValueConverter<T> {
    public fun publicToRealmValue(value: T?): RealmValue
    public fun realmValueToPublic(realmValue: RealmValue): T?
}

/**
 * Interface for converting between public user facing type and library storage types.
 *
 * This corresponds to step 1. of the overall conversion described in the top of this file.
 */
internal interface PublicConverter<T, S> {
    public fun fromPublic(value: T?): S?
    public fun toPublic(value: S?): T?
}

/**
 * Interface for converting between library storage types and C-API input/output values.
 *
 * This corresponds to step 2. of the overall conversion described in the top of this file.
 */
internal interface StorageTypeConverter<T> {
    public fun fromRealmValue(realmValue: RealmValue): T? = realmValueToAny(realmValue) as T?
    public fun toRealmValue(value: T?): RealmValue = anyToRealmValue(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun realmValueToAny(realmValue: RealmValue): Any? = realmValue.value
public inline fun anyToRealmValue(value: Any?): RealmValue = RealmValue(value)

/**
 * Composite converters that combines a [PublicConverter] and a [StorageTypeConverter] into a
 * [RealmValueConverter].
 */
internal abstract class CompositeConverter<T, S> :
    RealmValueConverter<T>, PublicConverter<T, S>, StorageTypeConverter<S> {
    override fun publicToRealmValue(value: T?): RealmValue = toRealmValue(fromPublic(value))
    override fun realmValueToPublic(realmValue: RealmValue): T? =
        toPublic(fromRealmValue(realmValue))
}

// RealmValueConverter with default pass-through public-to-storage-type implementation
internal abstract class PassThroughPublicConverter<T> : CompositeConverter<T, T>() {
    override fun fromPublic(value: T?): T? = passthrough(value) as T?
    override fun toPublic(value: T?): T? = passthrough(value) as T?
}

// Top level methods to allow inlining from compiler plugin
public inline fun passthrough(value: Any?): Any? = value

// Static converters
internal object StaticPassThroughConverter : PassThroughPublicConverter<Any>()

internal object ByteConverter : CompositeConverter<Byte, Long>() {
    override inline fun fromPublic(value: Byte?): Long? = byteToLong(value)
    override inline fun toPublic(value: Long?): Byte? = longToByte(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun byteToLong(value: Byte?): Long? = value?.let { it.toLong() }
public inline fun longToByte(value: Long?): Byte? = value?.let { it.toByte() }

internal object CharConverter : CompositeConverter<Char, Long>() {
    override inline fun fromPublic(value: Char?): Long? = charToLong(value)
    override inline fun toPublic(value: Long?): Char? = longToChar(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun charToLong(value: Char?): Long? = value?.let { it.code.toLong() }
public inline fun longToChar(value: Long?): Char? = value?.let { it.toInt().toChar() }

internal object ShortConverter : CompositeConverter<Short, Long>() {
    override inline fun fromPublic(value: Short?): Long? = shortToLong(value)
    override inline fun toPublic(value: Long?): Short? = longToShort(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun shortToLong(value: Short?): Long? = value?.let { it.toLong() }
public inline fun longToShort(value: Long?): Short? = value?.let { it.toShort() }

internal object IntConverter : CompositeConverter<Int, Long>() {
    override inline fun fromPublic(value: Int?): Long? = intToLong(value)
    override inline fun toPublic(value: Long?): Int? = longToInt(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun intToLong(value: Int?): Long? = value?.let { it.toLong() }
public inline fun longToInt(value: Long?): Int? = value?.let { it.toInt() }

internal object RealmInstantConverter : PassThroughPublicConverter<RealmInstant>() {
    override inline fun fromRealmValue(realmValue: RealmValue): RealmInstant? =
        realmValueToRealmInstant(realmValue)
}

// Top level method to allow inlining from compiler plugin
public inline fun realmValueToRealmInstant(realmValue: RealmValue): RealmInstant? =
    realmValue.value?.let { RealmInstantImpl(it as Timestamp) }

internal object ObjectIdConverter : PassThroughPublicConverter<ObjectId>() {
    override inline fun fromRealmValue(realmValue: RealmValue): ObjectId? =
        realmValueToObjectId(realmValue)
}

// Top level method to allow inlining from compiler plugin
public inline fun realmValueToObjectId(realmValue: RealmValue): ObjectId? {
    return realmValue.value?.let {
        ObjectIdImpl(it as ObjectIdWrapper)
    }
}

internal object RealmUUIDConverter : PassThroughPublicConverter<RealmUUID>() {
    override inline fun fromRealmValue(realmValue: RealmValue): RealmUUID? =
        realmValueToRealmUUID(realmValue)
}

// Top level method to allow inlining from compiler plugin
public inline fun realmValueToRealmUUID(realmValue: RealmValue): RealmUUID? {
    return realmValue.value?.let { RealmUUIDImpl(it as UUIDWrapper) }
}

internal object ByteArrayConverter : PassThroughPublicConverter<ByteArray>() {
    override inline fun fromRealmValue(realmValue: RealmValue): ByteArray? =
        realmValueToByteArray(realmValue)
}

public inline fun realmValueToByteArray(realmValue: RealmValue): ByteArray? {
    return realmValue.value?.let { it as ByteArray }
}

@SharedImmutable
internal val primitiveTypeConverters: Map<KClass<*>, RealmValueConverter<*>> =
    mapOf<KClass<*>, RealmValueConverter<*>>(
        Byte::class to ByteConverter,
        Char::class to CharConverter,
        Short::class to ShortConverter,
        Int::class to IntConverter,
        RealmInstant::class to RealmInstantConverter,
        ObjectId::class to ObjectIdConverter,
        RealmUUID::class to RealmUUIDConverter,
        ByteArray::class to ByteArrayConverter
    ).withDefault { StaticPassThroughConverter }

// Dynamic default primitive value converter to translate primary keys and query arguments to RealmValues
internal object RealmValueArgumentConverter {
    fun convertArg(value: Any?): RealmValue {
        return value?.let {
            (primitiveTypeConverters.getValue(it::class) as RealmValueConverter<Any?>)
                .publicToRealmValue(value)
        } ?: RealmValue(null)
    }

    fun convertArgs(value: Array<out Any?>): Array<RealmValue> =
        value.map { convertArg(it) }.toTypedArray()
}

// Realm object converter that also imports (copyToRealm) objects when setting it
internal fun <T : BaseRealmObject> realmObjectConverter(
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): RealmValueConverter<T> {
    return object : PassThroughPublicConverter<T>() {
        override fun fromRealmValue(realmValue: RealmValue): T? =
        // TODO OPTIMIZE We could lookup the companion and keep a reference to
            //  `companion.newInstance` method to avoid repeated mediator lookups in Link.toRealmObject()
            realmValueToRealmObject(realmValue, clazz, mediator, realmReference)

        override fun toRealmValue(value: T?): RealmValue =
            realmObjectToRealmValue(value as BaseRealmObject?, mediator, realmReference)
    }
}

internal inline fun <T : BaseRealmObject> realmValueToRealmObject(
    realmValue: RealmValue,
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): T? {
    return realmValue.value?.let {
        (it as Link).toRealmObject(
            clazz,
            mediator,
            realmReference
        )
    }
}

internal inline fun realmObjectToRealmValue(
    value: BaseRealmObject?,
    mediator: Mediator,
    realmReference: RealmReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: ObjectCache = mutableMapOf()
): RealmValue {
    // FIXME Would we actually rather like to error out on managed objects from different versions?
    return RealmValue(
        value?.let {
            val realmObjectReference = value.realmObjectReference
            // If managed ...
            if (realmObjectReference != null) {
                // and from the same version we just use object as is
                if (realmObjectReference.owner == realmReference) {
                    value
                } else {
                    throw IllegalArgumentException(
                        """Cannot import an outdated object. Use findLatest(object) to find an 
                            |up-to-date version of the object in the given context before importing 
                            |it.
                        """.trimMargin()
                    )
                }
            } else {
                // otherwise we will import it
                copyToRealm(
                    mediator,
                    realmReference.asValidLiveRealmReference(),
                    value,
                    updatePolicy,
                    cache = cache
                )
            }.realmObjectReference
        }
    )
}

// Returns a converter fixed to convert objects of the given type in the context of the given mediator/realm
internal fun <T> converter(
    clazz: KClass<*>,
    mediator: Mediator,
    realmReference: RealmReference
): RealmValueConverter<T> {
    return if (realmObjectCompanionOrNull(clazz) != null || clazz in setOf<KClass<*>>(
            DynamicRealmObject::class,
            DynamicMutableRealmObject::class
        )
    ) {
        realmObjectConverter(
            clazz as KClass<out RealmObject>,
            mediator,
            realmReference
        )
    } else {
        primitiveTypeConverters.getValue(clazz)
    } as RealmValueConverter<T>
}
