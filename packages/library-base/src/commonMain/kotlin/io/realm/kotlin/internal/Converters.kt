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
import io.realm.kotlin.ext.asRealmObject
import io.realm.kotlin.internal.interop.MemTrackingAllocator
import io.realm.kotlin.internal.interop.RealmObjectInterop
import io.realm.kotlin.internal.interop.RealmQueryArgsTransport
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.internal.interop.ValueType
import io.realm.kotlin.internal.platform.realmObjectCompanionOrNull
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.ObjectId
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.BsonObjectId
import org.mongodb.kbson.Decimal128
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
    fun MemTrackingAllocator.publicToRealmValue(value: T?): RealmValue
    fun realmValueToPublic(realmValue: RealmValue): T?
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
    fun fromRealmValue(realmValue: RealmValue): T?
    fun MemTrackingAllocator.toRealmValue(value: T?): RealmValue
}

// Top level methods to allow inlining from compiler plugin
// No need to handle null values here since it's handled by the accessors
public inline fun realmValueToLong(transport: RealmValue): Long = transport.getLong()
public inline fun realmValueToBoolean(transport: RealmValue): Boolean = transport.getBoolean()
public inline fun realmValueToString(transport: RealmValue): String = transport.getString()
public inline fun realmValueToByteArray(transport: RealmValue): ByteArray = transport.getByteArray()
public inline fun realmValueToRealmInstant(transport: RealmValue): RealmInstant =
    RealmInstantImpl(transport.getTimestamp())
public inline fun realmValueToFloat(transport: RealmValue): Float = transport.getFloat()
public inline fun realmValueToDouble(transport: RealmValue): Double = transport.getDouble()
public inline fun realmValueToObjectId(transport: RealmValue): BsonObjectId =
    BsonObjectId(transport.getObjectIdBytes())
public inline fun realmValueToRealmObjectId(transport: RealmValue): ObjectId =
    ObjectIdImpl(transport.getObjectIdBytes())
public inline fun realmValueToRealmUUID(transport: RealmValue): RealmUUID = RealmUUIDImpl(transport.getUUIDBytes())
@OptIn(ExperimentalUnsignedTypes::class)
public inline fun realmValueToDecimal128(transport: RealmValue): Decimal128 =
    transport.getDecimal128Array().let { Decimal128.fromIEEE754BIDEncoding(it[1], it[0]) }

internal inline fun realmValueToRealmAny(
    transport: RealmValue,
    mediator: Mediator,
    owner: RealmReference,
    issueDynamicObject: Boolean = false
): RealmAny? {
    return realmValueToRealmAny(transport, mediator, owner, issueDynamicObject, false)
}

@Suppress("ComplexMethod", "NestedBlockDepth")
internal inline fun realmValueToRealmAny(
    transport: RealmValue,
    mediator: Mediator,
    owner: RealmReference,
    issueDynamicObject: Boolean,
    issueDynamicMutableObject: Boolean,
): RealmAny? {
    return when (transport.isNull()) {
        true -> null
        false -> when (val type = transport.getType()) {
            ValueType.RLM_TYPE_NULL -> null
            ValueType.RLM_TYPE_INT -> RealmAny.create(transport.getLong())
            ValueType.RLM_TYPE_BOOL -> RealmAny.create(transport.getBoolean())
            ValueType.RLM_TYPE_STRING -> RealmAny.create(transport.getString())
            ValueType.RLM_TYPE_BINARY -> RealmAny.create(transport.getByteArray())
            ValueType.RLM_TYPE_TIMESTAMP -> RealmAny.create(RealmInstantImpl(transport.getTimestamp()))
            ValueType.RLM_TYPE_FLOAT -> RealmAny.create(transport.getFloat())
            ValueType.RLM_TYPE_DOUBLE -> RealmAny.create(transport.getDouble())
            ValueType.RLM_TYPE_DECIMAL128 -> RealmAny.create(realmValueToDecimal128(transport))
            ValueType.RLM_TYPE_OBJECT_ID ->
                RealmAny.create(BsonObjectId(transport.getObjectIdBytes()))
            ValueType.RLM_TYPE_UUID -> RealmAny.create(RealmUUIDImpl(transport.getUUIDBytes()))
            ValueType.RLM_TYPE_LINK -> {
                if (issueDynamicObject) {
                    val clazz = when (issueDynamicMutableObject) {
                        true -> DynamicMutableRealmObject::class
                        false -> DynamicRealmObject::class
                    }
                    val realmObject = realmValueToRealmObject(transport, clazz, mediator, owner)
                    RealmAny.create(realmObject!!)
                } else {
                    val clazz = owner.schemaMetadata
                        .get(transport.getLink().classKey)
                        ?.clazz
                        ?: throw IllegalArgumentException("The object class is not present in the current schema - are you using an outdated schema version?")
                    val realmObject = realmValueToRealmObject(transport, clazz, mediator, owner)
                    RealmAny.create(realmObject!! as RealmObject, clazz as KClass<out RealmObject>)
                }
            }
            else -> throw IllegalArgumentException("Unsupported type: ${type.name}")
        }
    }
}

/**
 * Composite converters that combines a [PublicConverter] and a [StorageTypeConverter] into a
 * [RealmValueConverter].
 */
internal abstract class CompositeConverter<T, S> :
    RealmValueConverter<T>, PublicConverter<T, S>, StorageTypeConverter<S> {
    override fun MemTrackingAllocator.publicToRealmValue(value: T?): RealmValue {
        val storageValue = fromPublic(value)
        return toRealmValue(storageValue)
    }
    override fun realmValueToPublic(realmValue: RealmValue): T? {
        val fromRealmValue = fromRealmValue(realmValue)
        return toPublic(fromRealmValue)
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
    override fun fromRealmValue(realmValue: RealmValue): Long? =
        if (realmValue.isNull()) null else realmValue.getLong()
    override fun MemTrackingAllocator.toRealmValue(value: Long?): RealmValue =
        longTransport(value)
}

internal object BooleanConverter : PassThroughPublicConverter<Boolean>() {
    override fun fromRealmValue(realmValue: RealmValue): Boolean? =
        if (realmValue.isNull()) null else realmValue.getBoolean()
    override fun MemTrackingAllocator.toRealmValue(value: Boolean?): RealmValue =
        booleanTransport(value)
}

internal object StringConverter : PassThroughPublicConverter<String>() {
    override fun fromRealmValue(realmValue: RealmValue): String? =
        if (realmValue.isNull()) null else realmValue.getString()
    override fun MemTrackingAllocator.toRealmValue(value: String?): RealmValue =
        stringTransport(value)
}

internal object FloatConverter : PassThroughPublicConverter<Float>() {
    override fun fromRealmValue(realmValue: RealmValue): Float? =
        if (realmValue.isNull()) null else realmValue.getFloat()
    override fun MemTrackingAllocator.toRealmValue(value: Float?): RealmValue =
        floatTransport(value)
}

internal object DoubleConverter : PassThroughPublicConverter<Double>() {
    override fun fromRealmValue(realmValue: RealmValue): Double? =
        if (realmValue.isNull()) null else realmValue.getDouble()
    override fun MemTrackingAllocator.toRealmValue(value: Double?): RealmValue =
        doubleTransport(value)
}

// Converter for Core INT storage type (i.e. Byte, Short, Int and Char public types )
internal interface CoreIntConverter : StorageTypeConverter<Long> {
    override fun fromRealmValue(realmValue: RealmValue): Long? =
        if (realmValue.isNull()) null else realmValue.getLong()
    override fun MemTrackingAllocator.toRealmValue(value: Long?): RealmValue =
        longTransport(value)
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
    override inline fun fromRealmValue(realmValue: RealmValue): RealmInstant? =
        if (realmValue.isNull()) null else realmValueToRealmInstant(realmValue)
    override inline fun MemTrackingAllocator.toRealmValue(value: RealmInstant?): RealmValue =
        timestampTransport(value?.let { it as Timestamp })
}

internal object ObjectIdConverter : PassThroughPublicConverter<BsonObjectId>() {
    override inline fun fromRealmValue(realmValue: RealmValue): BsonObjectId? =
        if (realmValue.isNull()) null else realmValueToObjectId(realmValue)

    override inline fun MemTrackingAllocator.toRealmValue(value: BsonObjectId?): RealmValue =
        objectIdTransport(value?.toByteArray())
}

// Top level methods to allow inlining from compiler plugin
public inline fun objectIdToRealmObjectId(value: BsonObjectId?): ObjectId? =
    value?.let { ObjectIdImpl(it.toByteArray()) }

internal object RealmObjectIdConverter : PassThroughPublicConverter<ObjectId>() {
    override inline fun fromRealmValue(realmValue: RealmValue): ObjectId? =
        if (realmValue.isNull()) null else realmValueToRealmObjectId(realmValue)

    override inline fun MemTrackingAllocator.toRealmValue(value: ObjectId?): RealmValue =
        objectIdTransport(value?.let { it as ObjectIdImpl }?.bytes)
}

// Top level methods to allow inlining from compiler plugin
public inline fun realmObjectIdToObjectId(value: ObjectId?): BsonObjectId? =
    value?.let { BsonObjectId((it as ObjectIdImpl).bytes) }

internal object RealmUUIDConverter : PassThroughPublicConverter<RealmUUID>() {
    override inline fun fromRealmValue(realmValue: RealmValue): RealmUUID? =
        if (realmValue.isNull()) null else realmValueToRealmUUID(realmValue)
    override inline fun MemTrackingAllocator.toRealmValue(value: RealmUUID?): RealmValue =
        uuidTransport(value?.bytes)
}

internal object ByteArrayConverter : PassThroughPublicConverter<ByteArray>() {
    override inline fun fromRealmValue(realmValue: RealmValue): ByteArray? =
        if (realmValue.isNull()) null else realmValueToByteArray(realmValue)
    override inline fun MemTrackingAllocator.toRealmValue(value: ByteArray?): RealmValue =
        byteArrayTransport(value)
}

internal object Decimal128Converter : PassThroughPublicConverter<Decimal128>() {
    override inline fun fromRealmValue(realmValue: RealmValue): Decimal128? =
        if (realmValue.isNull()) null else realmValueToDecimal128(realmValue)

    override inline fun MemTrackingAllocator.toRealmValue(value: Decimal128?): RealmValue =
        decimal128Transport(value)
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
        Double::class to DoubleConverter,
        Decimal128::class to Decimal128Converter
    )

// Dynamic default primitive value converter to translate primary keys and query arguments to RealmValues
@Suppress("NestedBlockDepth")
internal object RealmValueArgumentConverter {
    fun MemTrackingAllocator.convertArg(value: Any?): RealmValue {
        return value?.let {
            when (value) {
                is RealmObject -> {
                    val objRef = realmObjectToRealmReferenceOrError(value)
                    realmObjectTransport(objRef)
                }
                is RealmAny -> realmAnyToRealmValue(value)
                else -> {
                    primitiveTypeConverters[it::class]?.let { converter ->
                        with(converter as RealmValueConverter<Any?>) {
                            publicToRealmValue(value)
                        }
                    } ?: throw IllegalArgumentException("Cannot use object of type ${value::class::simpleName} as query argument")
                }
            }
        } ?: nullTransport()
    }

    fun MemTrackingAllocator.convertToQueryArgs(
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
        override fun fromRealmValue(realmValue: RealmValue): T? =
            realmValueToRealmObject(realmValue, clazz, mediator, realmReference)

        override fun MemTrackingAllocator.toRealmValue(value: T?): RealmValue =
            realmObjectTransport(
                value?.let { realmObjectToRealmReferenceOrError(it) as RealmObjectInterop }
            )
    }
}

@Suppress("OVERRIDE_BY_INLINE", "NestedBlockDepth")
internal fun realmAnyConverter(
    mediator: Mediator,
    realmReference: RealmReference,
    issueDynamicObject: Boolean = false,
    issueDynamicMutableObject: Boolean = false
): RealmValueConverter<RealmAny?> {
    return object : PassThroughPublicConverter<RealmAny?>() {
        override inline fun fromRealmValue(realmValue: RealmValue): RealmAny? {
            return when (realmValue.isNull()) {
                true -> null
                false -> when (val type = realmValue.getType()) {
                    ValueType.RLM_TYPE_INT -> RealmAny.create(realmValue.getLong())
                    ValueType.RLM_TYPE_BOOL -> RealmAny.create(realmValue.getBoolean())
                    ValueType.RLM_TYPE_STRING -> RealmAny.create(realmValue.getString())
                    ValueType.RLM_TYPE_BINARY -> RealmAny.create(realmValue.getByteArray())
                    ValueType.RLM_TYPE_TIMESTAMP ->
                        RealmAny.create(RealmInstantImpl(realmValue.getTimestamp()))
                    ValueType.RLM_TYPE_FLOAT -> RealmAny.create(realmValue.getFloat())
                    ValueType.RLM_TYPE_DOUBLE -> RealmAny.create(realmValue.getDouble())
                    ValueType.RLM_TYPE_DECIMAL128 -> RealmAny.create(
                        realmValueToDecimal128(
                            realmValue
                        )
                    )
                    ValueType.RLM_TYPE_OBJECT_ID ->
                        RealmAny.create(BsonObjectId(realmValue.getObjectIdBytes()))
                    ValueType.RLM_TYPE_UUID -> RealmAny.create(RealmUUIDImpl(realmValue.getUUIDBytes()))
                    ValueType.RLM_TYPE_LINK -> {
                        val link = realmValue.getLink()
                        val clazz = if (issueDynamicObject) {
                            if (issueDynamicMutableObject) {
                                DynamicMutableRealmObject::class
                            } else {
                                DynamicRealmObject::class
                            }
                        } else {
                            realmReference.schemaMetadata
                                .get(link.classKey)
                                ?.clazz
                                ?: throw IllegalArgumentException("The object class is not present in the current schema - are you using an outdated schema version?")
                        }
                        val internalObject = mediator.createInstanceOf(clazz)
                        val obj = internalObject.link(
                            realmReference,
                            mediator,
                            clazz,
                            link
                        )
                        when (issueDynamicObject) {
                            true -> when (issueDynamicMutableObject) {
                                true -> RealmAny.create(obj as DynamicMutableRealmObject)
                                else -> RealmAny.create(obj as DynamicRealmObject)
                            }
                            false -> RealmAny.create(
                                obj as RealmObject,
                                clazz as KClass<out RealmObject>
                            )
                        }
                    }
                    else -> throw IllegalArgumentException("Invalid type '$type' for RealmValue.")
                }
            }
        }

        override inline fun MemTrackingAllocator.toRealmValue(value: RealmAny?): RealmValue {
            return realmAnyToRealmValueWithObjectImport(
                value,
                mediator,
                realmReference,
                issueDynamicObject,
            )
        }
    }
}

/**
 * Used for converting values to query arguments. Importing objects isn't allowed here.
 */
internal inline fun MemTrackingAllocator.realmAnyToRealmValueWithObjectImport(
    value: RealmAny?,
    mediator: Mediator,
    realmReference: RealmReference,
    issueDynamicObject: Boolean = false
): RealmValue {
    return when (value) {
        null -> nullTransport()
        else -> when (value.type) {
            RealmAny.Type.OBJECT -> {
                val obj = when (issueDynamicObject) {
                    true -> value.asRealmObject<DynamicRealmObject>()
                    false -> value.asRealmObject<RealmObject>()
                }
                val objRef = realmObjectToRealmReferenceWithImport(obj, mediator, realmReference)
                realmObjectTransport(objRef as RealmObjectInterop)
            }
            else -> realmAnyPrimitiveToRealmValue(value)
        }
    }
}

/**
 * Used for converting RealmAny values to RealmValues suitable for query arguments.
 * Importing objects isn't allowed here.
 */
internal inline fun MemTrackingAllocator.realmAnyToRealmValue(value: RealmAny?): RealmValue {
    return when (value) {
        null -> nullTransport()
        else -> when (value.type) {
            RealmAny.Type.OBJECT -> {
                val objRef = realmObjectToRealmReferenceOrError(value.asRealmObject())
                realmObjectTransport(objRef)
            }
            else -> realmAnyPrimitiveToRealmValue(value)
        }
    }
}

/**
 * Used for converting primitive values to RealmValues.
 */
private inline fun MemTrackingAllocator.realmAnyPrimitiveToRealmValue(value: RealmAny): RealmValue {
    return when (value.type) {
        RealmAny.Type.INT -> longTransport(value.asLong())
        RealmAny.Type.BOOL -> booleanTransport(value.asBoolean())
        RealmAny.Type.STRING -> stringTransport(value.asString())
        RealmAny.Type.BINARY -> byteArrayTransport(value.asByteArray())
        RealmAny.Type.TIMESTAMP -> timestampTransport(value.asRealmInstant() as RealmInstantImpl)
        RealmAny.Type.FLOAT -> floatTransport(value.asFloat())
        RealmAny.Type.DOUBLE -> doubleTransport(value.asDouble())
        RealmAny.Type.DECIMAL128 -> decimal128Transport(value.asDecimal128())
        RealmAny.Type.OBJECT_ID -> objectIdTransport(value.asObjectId().toByteArray())
        RealmAny.Type.UUID -> uuidTransport(value.asRealmUUID().bytes)
        else -> throw UnsupportedOperationException("If you want to convert a 'RealmAny' instance containing an object to a 'RealmValue' use 'realmAnyToRealmValue' (when working with 'RealmQuery') or 'realmAnyToRealmValueWithObjectImport' (when using an accessor).")
    }
}

internal inline fun <T : BaseRealmObject> realmValueToRealmObject(
    transport: RealmValue,
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): T? {
    return when {
        transport.isNull() -> null
        else -> transport.getLink().toRealmObject(clazz, mediator, realmReference)
    }
}

// Will return a managed realm object reference or null. If the object is unmanaged it will be
// imported according to the update policy. If the object is an outdated object it will throw an
// error.
internal inline fun realmObjectToRealmReferenceWithImport(
    value: BaseRealmObject?,
    mediator: Mediator,
    realmReference: RealmReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: UnmanagedToManagedObjectCache = mutableMapOf()
): RealmObjectReference<out BaseRealmObject>? {
    return realmObjectWithImport(value, mediator, realmReference, updatePolicy, cache)
        ?.realmObjectReference
}

// Will return a managed realm object or null. If the object is unmanaged it will be imported
// according to the update policy. If the object is an outdated object it will throw an error.
internal inline fun realmObjectWithImport(
    value: BaseRealmObject?,
    mediator: Mediator,
    realmReference: RealmReference,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: UnmanagedToManagedObjectCache = mutableMapOf()
): BaseRealmObject? {
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
        }
    }
}

// Will return a managed realm object reference (or null) or throw when called with an unmanaged
// object
internal inline fun realmObjectToRealmReferenceOrError(
    value: BaseRealmObject?
): RealmObjectReference<out BaseRealmObject>? {
    return value?.let {
        value.runIfManaged { this }
            ?: throw IllegalArgumentException("Cannot lookup unmanaged objects in realm")
    }
}

// Returns a converter fixed to convert objects of the given type in the context of the given mediator/realm
internal fun <T> converter(
    clazz: KClass<T & Any>,
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
        ) as RealmValueConverter<T>
    } else if (clazz == RealmAny::class) {
        realmAnyConverter(mediator, realmReference) as RealmValueConverter<T>
    } else {
        primitiveTypeConverters.getValue(clazz) as RealmValueConverter<T>
    }
}
