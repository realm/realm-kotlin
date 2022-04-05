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
import io.realm.internal.interop.RealmValue
import io.realm.internal.interop.Link
import io.realm.internal.interop.Timestamp
import io.realm.internal.platform.realmObjectCompanionOrNull
import io.realm.schema.RealmStorageType
import kotlin.reflect.KClass

// From realm value to storage type
internal typealias CinteropGetter<S> = (io.realm.internal.interop.RealmValue) -> S?
// To realm value from storage type
internal typealias CinteropSetter<S> = (S?) -> RealmValue
//
internal typealias CustomConverter<T, S> = (T?) -> S?

internal fun defaultStorageType(clazz: KClass<*>): RealmStorageType {
    return when(clazz) {
        Boolean::class -> RealmStorageType.BOOL
        Byte::class -> RealmStorageType.INT
        Char::class -> RealmStorageType.INT
        Short::class -> RealmStorageType.INT
        Int::class -> RealmStorageType.INT
        Long::class -> RealmStorageType.INT
        String::class -> RealmStorageType.STRING
        Float::class -> RealmStorageType.FLOAT
        Double::class -> RealmStorageType.DOUBLE
        RealmInstant::class -> RealmStorageType.TIMESTAMP
        else -> {
            realmObjectCompanionOrNull(clazz)?.let { RealmStorageType.OBJECT}
                ?: throw IllegalArgumentException("ASDF")
        }
    }
}

public fun byteToLong(value: Byte?): Long? = value?.let { it.toLong()}
public fun charToLong(value: Char?): Long? = value?.let { it.code.toLong()}
public fun shortToLong(value: Short?): Long? = value?.let { it.toLong()}
public fun intToLong(value: Short?): Long? = value?.let { it.toLong()}
public fun longToByte(value: Long?): Byte? = value?.let { it.toByte() }
public fun longToChar(value: Long?): Char? = value?.let { it.toInt().toChar() }
public fun longToShort(value: Long?): Short? = value?.let { it.toShort() }
public fun longToInt(value: Long?): Int? = value?.let { it.toInt() }
public val defaultFromPublicType : Map<KClass<*>, CustomConverter<*, *>> = mapOf(
    Byte::class to (::byteToLong),
    Char::class to (::charToLong),
    Short::class to (::shortToLong),
    Int::class to (::intToLong),
)
public val defaultToPublicType : Map<KClass<*>, CustomConverter<*, *>> = mapOf(
    Byte::class to (::longToByte),
    Char::class to (::longToChar),
    Short::class to (::longToShort),
    Int::class to (::longToInt),
)

internal typealias RealmGetter<T> = (RealmValue) -> T?
internal typealias RealmSetter<T> = (T?) -> RealmValue
internal fun interface RealmConverterGetter<T> {
    public fun fromRealmValue(value: RealmValue): T?
}
internal fun interface RealmConverterSetter<T> {
    public fun toRealmValue(value: T?): RealmValue
}
internal interface RealmConverter<T> : RealmConverterGetter<T>, RealmConverterSetter<T>

internal fun <T: Any> converter(publicType: KClass<T>, mediator: Mediator, realm: RealmReference): RealmConverter<T> {
    val storageType = defaultStorageType(publicType)
    val cinteropGetter: CinteropGetter<Any> = when(storageType) {
        RealmStorageType.TIMESTAMP -> { realmValue: RealmValue -> realmValue.value?.let { RealmInstantImpl(it as Timestamp) } }
        RealmStorageType.OBJECT -> { realmValue: RealmValue ->
            realmValue.value?.let {
                (it as Link).toRealmObject(
                    publicType as KClass<out RealmObject>,
                    mediator,
                    realm
                )
            }
        }
        else ->
            { realmValue: RealmValue -> realmValue.value}
    }
    val cinteropSetter: CinteropSetter<Any> = when(storageType){
        RealmStorageType.OBJECT -> { realmObject ->
            RealmValue((copyToRealm(mediator, realm.asValidLiveRealmReference(), realmObject) as RealmObjectInternal).`$realm$objectReference`)
        }
        else -> { value: Any? -> RealmValue(value) }
    }

    val getter: (Any?) -> T? = defaultToPublicType.getOrElse(publicType, { () -> { value -> value as C }} ) as (Any?) -> T?
        Byte::class -> { value -> value?.let {(it as Long).toByte() as T } }
        Char::class -> { value -> value?.let {(it as Long).toInt().toChar() as T }}
        Short::class -> { value -> value?.let {(it as Long).toShort() as T}}
        Int::class -> { value -> value?.let {(it as Long).toInt() as T }}
        else -> { value -> value as T? }
    }
    val setter: (T?) -> Any? = when(publicType) {
        Byte::class -> { value -> value?.let { (it as Byte).toLong() } }
        Char::class -> { value -> value?.let { (it as Char).code.toLong() } }
        Short::class -> { value -> value?.let { (it as Short).toLong() } }
        Int::class -> { value -> value?.let { (it as Int).toLong() } }
        else -> { value -> value as Any? }
    }
    return object: RealmConverter<T> {
        override fun fromRealmValue(realmValue: RealmValue): T? {
            return getter(cinteropGetter(realmValue))
        }
        override fun toRealmValue(value: T?): RealmValue {
            return cinteropSetter(setter(value))
        }
    }
}

// typedef enum realm_value_type {
//     RLM_TYPE_NULL,
//     RLM_TYPE_INT,
//     RLM_TYPE_BOOL,
//     RLM_TYPE_STRING,
//     RLM_TYPE_BINARY,
//     RLM_TYPE_TIMESTAMP,
//     RLM_TYPE_FLOAT,
//     RLM_TYPE_DOUBLE,
//     RLM_TYPE_DECIMAL128,
//     RLM_TYPE_OBJECT_ID,
//     RLM_TYPE_LINK,
//     RLM_TYPE_UUID,
// } realm_value_type_e;

// Better naming
// public fun interface CinteropGetter<S : Any> {
//     public fun realmValueToStorageType(realmValue: io.realm.internal.interop.RealmValue, clazz: KClass<out S>): S?
// }
// public fun interface CinteropSetter<S> {
//     public fun storageTypeToRealmValue(value: S?): io.realm.internal.interop.RealmValue
// }
// Public
// public fun interface CustomGetter<T, S : Any> {
//     public fun fromStorageType(value: T?, clazz: KClass<out S>): S?
// }
// public fun interface CustomSetter<T, S> {
//     public fun toStorageType(storageType: S?): T?
// }
// Could be used to annotate a property
// public interface CustomConverter<T, S : Any>: CustomGetter<T, S>, CustomSetter<T, S>
// public interface Converter<T, S : Any> : CinteropGetter<S>, CinteropSetter<S>, CustomConverter<T, S>

// public object IdentityConverter : Converter<Any, Any> {
//     override fun realmValueToStorageType(realmValue: io.realm.internal.interop.RealmValue, clazz: KClass<out Any>): Any? = realmValue.value
//     override fun storageTypeToRealmValue(value: Any?): io.realm.internal.interop.RealmValue = RealmValue(value)
//     override fun fromStorageType(value: Any?, clazz: KClass<out Any>): Any? = value
//     override fun toStorageType(storageType: Any?): Any? = storageType
// }
// public object ByteConverter : CustomConverter<Byte, Long> {
//     override fun fromStorageType(value: Byte?, clazz: KClass<out Long>): Long? = value?.toLong()
//     override fun toStorageType(storageType: Long?): Byte? = storageType?.toByte()
// }
// public object IntConverter : CustomConverter<Int, Long> {
//     override fun fromStorageType(value: Int?, clazz: KClass<out Long>): Long? = value?.toLong()
//     override fun toStorageType(storageType: Long?): Int? = storageType?.toInt()
// }
// public object DynamicConverter : Converter<Any, Any> {
//     override fun toStorageType(value: Any?): Any? {
//         // println("toStorageType: $value ${value?.let{ it::class}} ")
//         return when (value) {
//             is Byte -> value.toLong()
//             is Char -> value.code.toLong()
//             is Short -> value.toLong()
//             is Int -> value.toLong()
//             else -> value
//         }
//     }
//     override fun fromStorageType(value: Any?, clazz: KClass<out Any>): Any? {
//         return value?.let {
//             when(clazz) {
//                 Byte::class -> (value as Long).toByte()
//                 Char::class -> (value as Long).toInt().toChar()
//                 Short::class -> (value as Long).toShort()
//                 Int::class -> (value as Long).toInt()
//                 else -> value
//             }
//         }
//     }
//
//     override fun realmValueToStorageType(
//         realmValue: io.realm.internal.interop.RealmValue,
//         clazz: KClass<out Any>
//     ): Any? {
//         return realmValue.value?.let {
//             when(clazz) {
//                 RealmInstant::class -> RealmInstantImpl(it as Timestamp)
//                 else -> it
//             }
//         }
//     }
//
//     override fun storageTypeToRealmValue(value: Any?): io.realm.internal.interop.RealmValue {
//         return RealmValue(value)
//     }
// }
