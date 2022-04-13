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

package io.realm.internal

import io.realm.RealmInstant
import io.realm.RealmObject
import io.realm.dynamic.DynamicMutableRealmObject
import io.realm.dynamic.DynamicRealmObject
import io.realm.internal.interop.Link
import io.realm.internal.interop.RealmValue
import io.realm.internal.interop.Timestamp
import io.realm.internal.platform.realmObjectCompanionOrNull
import kotlin.reflect.KClass

// Interface for converting storage types (Kotlin representation of Core values) to C-API RealmValue
public interface StorageTypeConverter<T> {
    public fun fromRealmValue(realmValue: RealmValue): T? = realmValueToAny(realmValue) as T?
    public fun toRealmValue(value: T?): RealmValue = anyToRealmValue(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun realmValueToAny(realmValue: RealmValue): Any? = realmValue.value
public inline fun anyToRealmValue(value: Any?): RealmValue = RealmValue(value)

// Interface for converting public types to storage types (Kotlin representation of Core values)
public interface PublicConverter<T, S> {
    public fun fromPublic(value: T?): S?
    public fun toPublic(value: S?): T?
}

// Interface for converting public type to C-API RealmValue
public interface RealmValueConverter<T> {
    public fun publicToRealmValue(value: T?): RealmValue
    public fun realmValueToPublic(realmValue: RealmValue): T?
}

public interface ConverterInternal<T, S> :
    RealmValueConverter<T>, PublicConverter<T, S>, StorageTypeConverter<S> {
    override fun publicToRealmValue(value: T?): RealmValue = toRealmValue(fromPublic(value))
    override fun realmValueToPublic(realmValue: RealmValue): T? =
        toPublic(fromRealmValue(realmValue))
}

// Converter with default identity conversion implementation
internal interface IdentityConverter<T> : ConverterInternal<T, T>, StorageTypeConverter<T> {
    override fun fromPublic(value: T?): T? = identity(value) as T?
    override fun toPublic(value: T?): T? = identity(value) as T?
}

// Top level methods to allow inlining from compiler plugin
public inline fun identity(value: Any?): Any? = value

// Static converters
public object StaticIdentityConverter : IdentityConverter<Any>

public object ByteConverter : ConverterInternal<Byte, Long> {
    override inline fun fromPublic(value: Byte?): Long? = byteToLong(value)
    override inline fun toPublic(value: Long?): Byte? = longToByte(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun byteToLong(value: Byte?): Long? = value?.let { it.toLong() }
public inline fun longToByte(value: Long?): Byte? = value?.let { it.toByte() }

internal object CharConverter : ConverterInternal<Char, Long> {
    override inline fun fromPublic(value: Char?): Long? = charToLong(value)
    override inline fun toPublic(value: Long?): Char? = longToChar(value)
}

// Top level methods to allow inlining from compiler plugin
public inline fun charToLong(value: Char?): Long? = value?.let { it.code.toLong() }
public inline fun longToChar(value: Long?): Char? = value?.let { it.toInt().toChar() }

internal object ShortConverter : ConverterInternal<Short, Long> {
    override inline fun fromPublic(value: Short?): Long? = value?.let { it.toLong() }
    override inline fun toPublic(value: Long?): Short? = value?.let { it.toShort() }
}

// Top level methods to allow inlining from compiler plugin
public inline fun shortToLong(value: Short?): Long? = value?.let { it.toLong() }
public inline fun longToShort(value: Long?): Short? = value?.let { it.toShort() }

internal object IntConverter : ConverterInternal<Int, Long> {
    override inline fun fromPublic(value: Int?): Long? = value?.let { it.toLong() }
    override inline fun toPublic(value: Long?): Int? = value?.let { it.toInt() }
}

// Top level methods to allow inlining from compiler plugin
public inline fun intToLong(value: Int?): Long? = value?.let { it.toLong() }
public inline fun longToInt(value: Long?): Int? = value?.let { it.toInt() }

internal object RealmInstantConverter :
    IdentityConverter<RealmInstant>, StorageTypeConverter<RealmInstant> {
    override inline fun fromRealmValue(realmValue: RealmValue): RealmInstant? =
        realmValueToRealmInstant(realmValue)
}

// Top level method to allow inlining from compiler plugin
public inline fun realmValueToRealmInstant(realmValue: RealmValue): RealmInstant? =
    realmValue.value?.let { RealmInstantImpl(it as Timestamp) }

internal val primitiveTypeConverters: Map<KClass<*>, ConverterInternal<*, *>> =
    mapOf<KClass<*>, ConverterInternal<*, *>>(
        Byte::class to ByteConverter,
        Char::class to CharConverter,
        Short::class to ShortConverter,
        Int::class to IntConverter,
        RealmInstant::class to RealmInstantConverter
    ).withDefault { StaticIdentityConverter }

// Dynamic default primitive value converter to translate primary keys and query arguments to RealmValues
public object RealmValueArgumentConverter : RealmValueConverter<Any?> {
    override fun publicToRealmValue(value: Any?): RealmValue {
        return value?.let {
            (primitiveTypeConverters.getValue(it::class) as RealmValueConverter<Any?>)
                .publicToRealmValue(value)
        } ?: RealmValue(null)
    }

    override fun realmValueToPublic(realmValue: RealmValue): Any? {
        error("RealmValueArgumentConverter cannot convert RealmValues to public types")
    }
}

// Realm object converter that also imports (copyToRealm) objects when setting it
public fun <T : RealmObject> realmObjectConverter(
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): ConverterInternal<T, T> {
    return object : IdentityConverter<T> {
        override fun fromRealmValue(realmValue: RealmValue): T? =
            // TODO OPTIMIZE We could lookup the companion and keep a reference to
            //  `companion.newInstance` method to avoid repeated mediator lookups in Link.toRealmObject()
            realmValueToRealmObject(realmValue, clazz, mediator, realmReference)

        override fun toRealmValue(value: T?): RealmValue =
            realmObjectToRealmValue(value, mediator, realmReference)
    }
}

internal inline fun <T : RealmObject> realmValueToRealmObject(
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

internal inline fun <T : RealmObject> realmObjectToRealmValue(
    value: T?,
    mediator: Mediator,
    realmReference: RealmReference
): RealmValue {
    val newValue = value?.let {
        val realmObjectReference = it.realmObjectReference
        // FIXME Would we actually rather like to error out on managed objects from different versions?
        if (realmObjectReference != null && realmObjectReference.owner == realmReference) {
            // If managed and from the same version we just use object as is
            it
        } else {
            // otherwise we will import it
            copyToRealm(mediator, realmReference.asValidLiveRealmReference(), it)
        }
    }
    return RealmValue(newValue?.realmObjectReference)
}

// Returns a converter fixed to convert objects of the given type in the context of the given mediator/realm
public fun <T : Any> converter(
    clazz: KClass<T>,
    mediator: Mediator,
    realmReference: RealmReference
): ConverterInternal<T, Any> {
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
    } as ConverterInternal<T, Any>
}
