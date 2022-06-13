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

import io.realm.kotlin.internal.interop.UUIDWrapper
import io.realm.kotlin.internal.util.toHexString
import io.realm.kotlin.types.RealmUUID
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.random.Random

@Suppress("MagicNumber")
internal class RealmUUIDImpl : RealmUUID, UUIDWrapper {

    private val _bytes: ByteArray

    override val bytes: ByteArray
        get() = _bytes

    constructor() {
        val bytes = Random.nextBytes(UUID_BYTE_SIZE)

        // Set uuid to version 4, 6th byte must be 0x40
        bytes[6] = bytes[6] and 0x0F.toByte()
        bytes[6] = bytes[6] or 0x40.toByte()

        // Set variant, 8th byte must be 0b10xxxxxx or 0b110xxxxx
        bytes[8] = bytes[8] and 0x3F.toByte()
        bytes[8] = bytes[8] or 0x80.toByte()

        _bytes = bytes
    }

    constructor(uuidString: String) {
        _bytes = parseHexString(uuidString)
    }

    constructor(bytes: ByteArray) {
        if (bytes.size != UUID_BYTE_SIZE)
            throw IllegalArgumentException("Invalid 'bytes' size ${bytes.size}, byte array size must be $UUID_BYTE_SIZE")

        _bytes = bytes
    }

    override fun equals(other: Any?): Boolean {
        return (other as RealmUUID).bytes.contentEquals(bytes)
    }

    override fun hashCode(): Int {
        return super.hashCode()
    }

    override fun toString(): String {
        return StringBuilder(_bytes.toHexString())
            .apply {
                for (index in HYPHEN_INDEXES) insert(index, '-')
            }
            .toString()
    }

    companion object {
        private const val UUID_BYTE_SIZE = 16
        private val HYPHEN_INDEXES = listOf(8, 13, 18, 23)
        private val VALUE_INDEXES = (0 until 36) - HYPHEN_INDEXES
        private val VALID_CHARS = ('0'..'9') + ('a'..'f') + ('A'..'F')

        /**
         * Validates and parses an UUID string representation into a byte array.
         */
        private fun parseHexString(uuidString: String): ByteArray {
            if (!isValid(uuidString)) {
                throw IllegalArgumentException("Invalid string representation of an UUID: '$uuidString'")
            }

            return ByteArray(UUID_BYTE_SIZE) { byteIndex ->
                val valueIndex = VALUE_INDEXES[byteIndex * 2]
                uuidString.substring(valueIndex, valueIndex + 2).toInt(16).toByte()
            }
        }

        @Suppress("ReturnCount")
        private fun isValid(uuidString: String): Boolean {
            // Check valid string length
            if (uuidString.length != 36) return false

            // Check hyphens are correctly located
            HYPHEN_INDEXES.forEach { index ->
                if (uuidString[index] != '-') return false
            }

            // Check valid hex values, ignoring hyphens
            VALUE_INDEXES.forEach { index ->
                if (uuidString[index] !in VALID_CHARS) return false
            }

            return true
        }
    }
}
