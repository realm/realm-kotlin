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

// TODO BENCHMARK: investigate performance between using this as value vs reference type
actual typealias RealmValueT = realm_value_t

@JvmInline
actual value class RealmValue actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getLong(): Long = value.integer
    actual inline fun getBoolean(): Boolean = value._boolean
    actual inline fun getString(): String = value.string
    actual inline fun getByteArray(): ByteArray = value.binary.data
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()
    actual inline fun getFloat(): Float = value.fnum
    actual inline fun getDouble(): Double = value.dnum
    actual inline fun getObjectIdBytes(): ByteArray = ByteArray(OBJECT_ID_BYTES_SIZE).also {
        value.object_id.bytes.mapIndexed { index, b -> it[index] = b.toByte() }
    }

    actual inline fun getUUIDBytes(): ByteArray = ByteArray(UUID_BYTES_SIZE).also {
        value.uuid.bytes.mapIndexed { index, b -> it[index] = b.toByte() }
    }

    actual inline fun getDecimal128Array(): ULongArray {
        val decimal128 = value.decimal128
        val w = decimal128.w
        val toULongArray = w.toULongArray()
        return toULongArray
    }

    actual inline fun getLink(): Link = value.asLink()

    override fun toString(): String {
        val valueAsString = when (getType()) {
            ValueType.RLM_TYPE_NULL -> "null"
            ValueType.RLM_TYPE_INT -> getLong()
            ValueType.RLM_TYPE_BOOL -> getBoolean()
            ValueType.RLM_TYPE_STRING -> getString()
            ValueType.RLM_TYPE_BINARY -> getByteArray().toString()
            ValueType.RLM_TYPE_TIMESTAMP -> getTimestamp().toString()
            ValueType.RLM_TYPE_FLOAT -> getFloat()
            ValueType.RLM_TYPE_DOUBLE -> getDouble()
            ValueType.RLM_TYPE_DECIMAL128 -> getDecimal128Array().toString()
            ValueType.RLM_TYPE_OBJECT_ID -> getObjectIdBytes().toString()
            ValueType.RLM_TYPE_LINK -> getLink().toString()
            ValueType.RLM_TYPE_UUID -> getUUIDBytes().toString()
            else -> "RealmValueTransport{type: UNKNOWN, value: UNKNOWN}"
        }
        return "RealmValueTransport{type: ${getType()}, value: $valueAsString}"
    }
}

actual typealias RealmQueryArgT = realm_query_arg_t

@JvmInline
actual value class RealmQueryArgsTransport(val value: RealmQueryArgT)

actual fun RealmValue.isNull(): Boolean = value.type == ValueType.RLM_TYPE_NULL.nativeValue
