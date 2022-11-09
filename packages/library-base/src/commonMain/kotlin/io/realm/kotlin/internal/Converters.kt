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
import io.realm.kotlin.ext.asBsonObjectId
import io.realm.kotlin.internal.interop.MemAllocator
import io.realm.kotlin.internal.interop.MemTrackingAllocator
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmQueryArgsTransport
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.internal.interop.UUIDWrapper
import io.realm.kotlin.internal.platform.realmObjectCompanionOrNull
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import kotlin.native.concurrent.SharedImmutable
import kotlin.reflect.KClass

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
    fun MemAllocator.publicToRealmValue(value: T?): RealmValue
    fun MemAllocator.realmValueToPublic(realmValue: RealmValue?): T?
}

/**
 * Interface for converting between public user facing type and library storage types.
 *
 * This corresponds to step 1. of the overall conversion described in the top of this file.
 */
internal interface PublicConverter<T, S> {
    fun fromPublic(value: T?): S?
    fun toPublic(value: S?): T?
}

/**
 * Interface for converting between library storage types and C-API input/output values.
 *
 * This corresponds to step 2. of the overall conversion described in the top of this file.
 */
internal interface StorageTypeConverter<T> {
    fun fromRealmValue(realmValue: RealmValue?): T?
    fun MemAllocator.toRealmValue(value: T?): RealmValue
}
// Top level methods to allow inlining from compiler plugin
public inline fun realmValueToLong(transport: RealmValue?): Long? =
    transport?.getLong()
public inline fun realmValueToBoolean(transport: RealmValue?): Boolean? =
    transport?.getBoolean()
public inline fun realmValueToString(transport: RealmValue?): String? =
    transport?.getString()
public inline fun realmValueToByteArray(transport: RealmValue?): ByteArray? =
    transport?.getByteArray()
public inline fun realmValueToRealmInstant(transport: RealmValue?): RealmInstant? =
    transport?.let { RealmInstantImpl(it.getTimestamp()) }
public inline fun realmValueToFloat(transport: RealmValue?): Float? =
    transport?.getFloat()
public inline fun realmValueToDouble(transport: RealmValue?): Double? =
    transport?.getDouble()
public inline fun realmValueToObjectId(transport: RealmValue?): BsonObjectId? =
    transport?.let { it.getObjectId() }
public inline fun realmValueToRealmObjectId(transport: RealmValue?): ObjectId? =
    transport?.let { ObjectIdImpl(it.getObjectId().toByteArray()) }
public inline fun realmValueToRealmUUID(transport: RealmValue?): RealmUUID? =
    transport?.let { RealmUUIDImpl(it.getUUIDWrapper()) }

/**
 * Composite converters that combines a [PublicConverter] and a [StorageTypeConverter] into a
 * [RealmValueConverter].
 */
internal abstract class CompositeConverter<T, S> :
    RealmValueConverter<T>, PublicConverter<T, S>, StorageTypeConverter<S> {
    override fun MemAllocator.publicToRealmValue(value: T?): RealmValue {
        val storageValue = fromPublic(value)
        val transport = toRealmValue(storageValue)
        return transport
    }
    override fun MemAllocator.realmValueToPublic(realmValue: RealmValue?): T? {
        val fromRealmValue = fromRealmValue(realmValue)
        val toPublic = toPublic(fromRealmValue)
        return toPublic
    }
}

// RealmValueConverter with default pass-through public-to-storage-type implementation
internal abstract class PassThroughPublicConverter<T> : CompositeConverter<T, T>() {
    override fun fromPublic(value: T?): T? = passthrough(value) as T?
    override fun toPublic(value: T?): T? = passthrough(value) as T?
}
// Top level methods to allow inlining from compiler plugin
public inline fun passthrough(value: Any?): Any? = value

// Passthrough converters
internal object LongConverter : PassThroughPublicConverter<Long>() {
    override fun fromRealmValue(realmValue: RealmValue?): Long? = realmValue?.getLong()
    override fun MemAllocator.toRealmValue(value: Long?): RealmValue = when (value) {
        null -> transportOf()
        else -> transportOf(value)
    }
}

internal object BooleanConverter : PassThroughPublicConverter<Boolean>() {
    override fun fromRealmValue(realmValue: RealmValue?): Boolean? = realmValue?.getBoolean()
    override fun MemAllocator.toRealmValue(value: Boolean?): RealmValue = when (value) {
        null -> transportOf()
        else -> transportOf(value)
    }
}

internal object StringConverter : PassThroughPublicConverter<String>() {
    override fun fromRealmValue(realmValue: RealmValue?): String? = realmValue?.getString()
    override fun MemAllocator.toRealmValue(value: String?): RealmValue = when (value) {
        null -> transportOf()
        else -> (this as MemTrackingAllocator).transportOf(value)
    }
}

internal object FloatConverter : PassThroughPublicConverter<Float>() {
    override fun fromRealmValue(realmValue: RealmValue?): Float? = realmValue?.getFloat()
    override fun MemAllocator.toRealmValue(value: Float?): RealmValue = when (value) {
        null -> transportOf()
        else -> transportOf(value)
    }
}

internal object DoubleConverter : PassThroughPublicConverter<Double>() {
    override fun fromRealmValue(realmValue: RealmValue?): Double? = realmValue?.getDouble()
    override fun MemAllocator.toRealmValue(value: Double?): RealmValue = when (value) {
        null -> transportOf()
        else -> transportOf(value)
    }
}

// Converter for Core INT storage type (i.e. Byte, Short, Int and Char public types )
internal interface CoreIntConverter : StorageTypeConverter<Long> {
    override fun fromRealmValue(realmValue: RealmValue?): Long? = realmValue?.getLong()
    override fun MemAllocator.toRealmValue(value: Long?): RealmValue =
        value?.let { transportOf(it) } ?: transportOf()
}

internal object ByteConverter : CoreIntConverter, CompositeConverter<Byte, Long>() {
    override inline fun fromPublic(value: Byte?): Long? = byteToLong(value)
    override inline fun toPublic(value: Long?): Byte? = longToByte(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun byteToLong(value: Byte?): Long? = value?.toLong()
public inline fun longToByte(value: Long?): Byte? = value?.toByte()

internal object CharConverter : CoreIntConverter, CompositeConverter<Char, Long>() {
    override inline fun fromPublic(value: Char?): Long? = charToLong(value)
    override inline fun toPublic(value: Long?): Char? = longToChar(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun charToLong(value: Char?): Long? = value?.code?.toLong()
public inline fun longToChar(value: Long?): Char? = value?.toInt()?.toChar()

internal object ShortConverter : CoreIntConverter, CompositeConverter<Short, Long>() {
    override inline fun fromPublic(value: Short?): Long? = shortToLong(value)
    override inline fun toPublic(value: Long?): Short? = longToShort(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun shortToLong(value: Short?): Long? = value?.toLong()
public inline fun longToShort(value: Long?): Short? = value?.toShort()

internal object IntConverter : CoreIntConverter, CompositeConverter<Int, Long>() {
    override inline fun fromPublic(value: Int?): Long? = intToLong(value)
    override inline fun toPublic(value: Long?): Int? = longToInt(value)
}
// Top level methods to allow inlining from compiler plugin
public inline fun intToLong(value: Int?): Long? = value?.toLong()
public inline fun longToInt(value: Long?): Int? = value?.toInt()

internal object RealmInstantConverter : PassThroughPublicConverter<RealmInstant>() {
    override inline fun fromRealmValue(realmValue: RealmValue?): RealmInstant? =
        realmValueToRealmInstant(realmValue)
    override inline fun MemAllocator.toRealmValue(value: RealmInstant?): RealmValue =
        value?.let { transportOf(it as Timestamp) }
            ?: transportOf()
}

internal object ObjectIdConverter : PassThroughPublicConverter<BsonObjectId>() {
    override inline fun fromRealmValue(realmValue: RealmValue?): BsonObjectId? =
        realmValueToObjectId(realmValue)

    override inline fun MemAllocator.toRealmValue(value: BsonObjectId?): RealmValue =
        value?.let { transportOf(it) }
            ?: transportOf()
}

// Top level methods to allow inlining from compiler plugin
public inline fun objectIdToRealmObjectId(value: BsonObjectId?): ObjectId? =
    value?.let { ObjectIdImpl(it.toByteArray()) }

internal object RealmObjectIdConverter : PassThroughPublicConverter<ObjectId>() {
    override inline fun fromRealmValue(realmValue: RealmValue?): ObjectId? =
        realmValueToRealmObjectId(realmValue)

    override inline fun MemAllocator.toRealmValue(value: ObjectId?): RealmValue =
        value?.let { transportOf(it.asBsonObjectId()) }
            ?: transportOf()
}

// Top level methods to allow inlining from compiler plugin
public inline fun realmObjectIdToObjectId(value: ObjectId?): BsonObjectId? =
    value?.let { BsonObjectId((it as ObjectIdImpl).bytes) }

internal object RealmUUIDConverter : PassThroughPublicConverter<RealmUUID>() {
    override inline fun fromRealmValue(realmValue: RealmValue?): RealmUUID? =
        realmValueToRealmUUID(realmValue)
    override inline fun MemAllocator.toRealmValue(value: RealmUUID?): RealmValue =
        value?.let { transportOf(value as UUIDWrapper) }
            ?: transportOf()
}

internal object ByteArrayConverter : PassThroughPublicConverter<ByteArray>() {
    override inline fun fromRealmValue(realmValue: RealmValue?): ByteArray? =
        realmValueToByteArray(realmValue)
    override inline fun MemAllocator.toRealmValue(value: ByteArray?): RealmValue =
        value?.let { (this as MemTrackingAllocator).transportOf(value) }
            ?: transportOf()
}

@SharedImmutable
internal val primitiveTypeConverters: Map<KClass<*>, RealmValueConverter<*>> =
    mapOf<KClass<*>, RealmValueConverter<*>>(
        Byte::class to ByteConverter,
        Char::class to CharConverter,
        Short::class to ShortConverter,
        Int::class to IntConverter,
        RealmInstant::class to RealmInstantConverter,
        RealmInstantImpl::class to RealmInstantConverter,
        BsonObjectId::class to ObjectIdConverter,
        ObjectId::class to RealmObjectIdConverter,
        ObjectIdImpl::class to RealmObjectIdConverter,
        RealmUUID::class to RealmUUIDConverter,
        RealmUUIDImpl::class to RealmUUIDConverter,
        ByteArray::class to ByteArrayConverter,
        String::class to StringConverter,
        Long::class to LongConverter,
        Boolean::class to BooleanConverter,
        Float::class to FloatConverter,
        Double::class to DoubleConverter
    )

// Dynamic default primitive value converter to translate primary keys and query arguments to RealmValues
@Suppress("NestedBlockDepth")
internal object RealmValueArgumentConverter {
    fun MemAllocator.convertArg(value: Any?): RealmValue {
        return value?.let {
            when (value) {
                is RealmObject -> {
                    val objRef = realmObjectToRealmValueOrError(value)
                    when (objRef) {
                        null -> transportOf()
                        else -> transportOf(objRef)
                    }
                }
                else -> {
                    primitiveTypeConverters[it::class]?.let { converter ->
                        with(converter as RealmValueConverter<Any?>) {
                            publicToRealmValue(value)
                        }
                    } ?: throw IllegalArgumentException("Cannot use object of type ${value::class::simpleName} as query argument")
                }
            }
        } ?: transportOf()
    }

    fun MemAllocator.convertToQueryArgs(
        queryArgs: Array<out Any?>
    ): Pair<Int, RealmQueryArgsTransport> {
        return queryArgs.map {
            convertArg(it)
        }.toTypedArray().let {
            Pair(queryArgs.size, queryArgsOf(it))
        }
    }
}

// Realm object converter that also imports (copyToRealm) objects when setting it
internal fun <T : BaseRealmObject> realmObjectConverter(
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): RealmValueConverter<T> {
    return object : PassThroughPublicConverter<T>() {
        // TODO OPTIMIZE We could lookup the companion and keep a reference to
        //  `companion.newInstance` method to avoid repeated mediator lookups in Link.toRealmObject()
        override fun fromRealmValue(realmValue: RealmValue?): T? =
            realmValueToRealmObject(realmValue, clazz, mediator, realmReference)

        override fun MemAllocator.toRealmValue(value: T?): RealmValue = when (value) {
            null -> transportOf()
            else -> transportOf(realmObjectToRealmValueOrError(value) as RealmObjectInterop)
        }
    }
}

internal inline fun <T : BaseRealmObject> realmValueToRealmObject(
    realmValue: RealmValue?,
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): T? {
    return realmValue?.getLink()
        ?.toRealmObject(
            clazz,
            mediator,
            realmReference
        )
}

// Will return a managed realm object reference or null. If the object is unmanaged it will be
// imported according to the update policy. If the object is an outdated object it will throw an
// error.
internal inline fun realmObjectToRef(
    value: BaseRealmObject?,
    mediator: Mediator,
    realmReference: RealmReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: ObjectCache = mutableMapOf()
): RealmObjectReference<out BaseRealmObject>? {
    return value?.let {
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
            copyToRealm(mediator, realmReference.asValidLiveRealmReference(), value, updatePolicy, cache = cache)
        }.realmObjectReference
    }
}

// Will return a RealmValue wrapping a managed realm object reference (or null) or throw when
// called with an unmanaged object
internal inline fun realmObjectToRealmValueOrError(
    value: BaseRealmObject?
): RealmObjectReference<out BaseRealmObject>? {
    return value?.let {
        value.runIfManaged { this }
            ?: throw IllegalArgumentException("Cannot lookup unmanaged objects in realm")
    }
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
