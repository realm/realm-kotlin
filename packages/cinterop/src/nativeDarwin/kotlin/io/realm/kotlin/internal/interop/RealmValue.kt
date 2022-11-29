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

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
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
    actual inline fun getObjectIdBytes(): ByteArray = memScoped {
        UByteArray(OBJECT_ID_BYTES_SIZE).let { byteArray ->
            byteArray.usePinned {
                val destination = it.addressOf(0)
                val source = value.object_id.bytes.getPointer(this@memScoped)
                memcpy(destination, source, OBJECT_ID_BYTES_SIZE.toULong())
            }
            byteArray.asByteArray()
        }
    }

    actual inline fun getUUIDBytes(): ByteArray = memScoped {
        UByteArray(UUID_BYTES_SIZE).let { byteArray ->
            byteArray.usePinned {
                val destination = it.addressOf(0)
                val source = value.uuid.bytes.getPointer(this@memScoped)
                memcpy(destination, source, UUID_BYTES_SIZE.toULong())
            }
            byteArray.asByteArray()
        }
    }

    actual inline fun getLink(): Link = value.asLink()

    actual inline fun isNull(): Boolean = value.type == ValueType.RLM_TYPE_NULL.nativeValue

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
            ValueType.RLM_TYPE_OBJECT_ID -> getObjectIdBytes().toString()
            ValueType.RLM_TYPE_LINK -> getLink().toString()
            ValueType.RLM_TYPE_UUID -> getUUIDBytes().toString()
            else -> "RealmValueTransport{type: UNKNOWN, value: UNKNOWN}"
        }
        return "RealmValueTransport{type: ${getType()}, value: $valueAsString}"
    }
}

actual typealias RealmQueryArgT = realm_query_arg

actual value class RealmQueryArgsTransport(val value: RealmQueryArgT)
