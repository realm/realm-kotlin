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

package io.realm.kotlin.internal.interop

import org.mongodb.kbson.ObjectId
import realm_wrapper.realm_query_arg
import realm_wrapper.realm_value

actual typealias RealmValueT = realm_value

actual value class RealmValue actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getLong(): Long = value.integer
    actual inline fun getBoolean(): Boolean = value.boolean
    actual inline fun getString(): String = value.string.toKotlinString()
    actual inline fun getByteArray(): ByteArray = value.asByteArray()
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()
    actual inline fun getFloat(): Float = value.fnum
    actual inline fun getDouble(): Double = value.dnum
    actual inline fun getObjectId(): ObjectId = value.asObjectId()
    actual inline fun getUUIDWrapper(): UUIDWrapper = value.asUUID()
    actual inline fun getLink(): Link = value.asLink()

    @Suppress("ComplexMethod")
    actual inline fun <reified T> get(): T {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val result = when (T::class) {
            Int::class -> value.integer.toInt()
            Short::class -> value.integer.toShort()
            Long::class -> value.integer
            Byte::class -> value.integer.toByte()
            Char::class -> value.integer.toInt().toChar()
            Boolean::class -> value.boolean
            String::class -> value.string.toKotlinString()
            ByteArray::class -> value.asByteArray()
            Timestamp::class -> value.asTimestamp()
            Float::class -> value.fnum
            Double::class -> value.dnum
            ObjectId::class -> value.asObjectId()
            UUIDWrapper::class -> value.asUUID()
            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
        }
        return result as T
    }

    override fun toString(): String {
        val valueAsString = when (val type = getType()) {
            ValueType.RLM_TYPE_NULL -> "null"
            ValueType.RLM_TYPE_INT -> getLong()
            ValueType.RLM_TYPE_BOOL -> getBoolean()
            ValueType.RLM_TYPE_STRING -> getString()
            ValueType.RLM_TYPE_BINARY -> getByteArray().toString()
            ValueType.RLM_TYPE_TIMESTAMP -> getTimestamp().toString()
            ValueType.RLM_TYPE_FLOAT -> getFloat()
            ValueType.RLM_TYPE_DOUBLE -> getDouble()
            ValueType.RLM_TYPE_OBJECT_ID -> getObjectId().toString()
            ValueType.RLM_TYPE_LINK -> getLink().toString()
            ValueType.RLM_TYPE_UUID -> getUUIDWrapper().toString()
            else -> "RealmValueTransport{type: UNKNOWN, value: UNKNOWN}"
        }
        return "RealmValueTransport{type: ${getType()}, value: $valueAsString}"
    }
}

actual typealias RealmQueryArgT = realm_query_arg

actual value class RealmQueryArgsTransport(val value: RealmQueryArgT)
