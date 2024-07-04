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

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import realm_wrapper.realm_query_arg
import realm_wrapper.realm_value
import realm_wrapper.realm_value_t

actual typealias RealmValueT = realm_value

actual class RealmValueList(actual val size: Int, val head: CPointer<realm_value_t>) {
    actual operator fun set(index: Int, value: RealmValue) {
        memcpy(head[index].ptr, value.value.ptr, sizeOf<realm_value_t>().toULong())
    }
}

actual value class RealmValue actual constructor(
    actual val value: RealmValueT,
) {
    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getType(): ValueType = ValueType.from(value.type)

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getLong(): Long = value.integer

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getBoolean(): Boolean = value.boolean

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getString(): String = value.string.toKotlinString()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getByteArray(): ByteArray = value.asByteArray()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getFloat(): Float = value.fnum

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getDouble(): Double = value.dnum

    @Suppress("NOTHING_TO_INLINE")
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

    @Suppress("NOTHING_TO_INLINE")
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

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getDecimal128Array(): ULongArray {
        val w = value.decimal128.w
        return ulongArrayOf(w[0], w[1])
    }

    @Suppress("NOTHING_TO_INLINE")
    actual inline fun getLink(): Link = value.asLink()

    @Suppress("NOTHING_TO_INLINE")
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
            ValueType.RLM_TYPE_DECIMAL128 -> getDecimal128Array().toString()
            ValueType.RLM_TYPE_OBJECT_ID -> getObjectIdBytes().toString()
            ValueType.RLM_TYPE_LINK -> getLink().toString()
            ValueType.RLM_TYPE_UUID -> getUUIDBytes().toString()
            else -> "RealmValueTransport{type: UNKNOWN, value: UNKNOWN}"
        }
        return "RealmValueTransport{type: ${getType()}, value: $valueAsString}"
    }
}

actual class RealmQueryArgumentList(val size: ULong, val head: realm_query_arg)
