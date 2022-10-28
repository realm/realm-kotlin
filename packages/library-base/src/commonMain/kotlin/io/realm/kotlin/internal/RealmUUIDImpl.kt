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
import io.realm.kotlin.internal.util.HEX_PATTERN
import io.realm.kotlin.internal.util.parseHex
import io.realm.kotlin.internal.util.toHexString
import io.realm.kotlin.types.RealmUUID
import kotlin.experimental.and
import kotlin.experimental.or
import kotlin.random.Random

@Suppress("MagicNumber")
// Public as constructor is inlined in accessor converter method (Converters.kt)
public class RealmUUIDImpl : RealmUUID, UUIDWrapper {
    override val bytes: ByteArray

    public constructor(wrapper: UUIDWrapper) : this(wrapper.bytes)

    public constructor() {
        bytes = Random.nextBytes(UUID_BYTE_SIZE).apply {
            // Set uuid to version 4, 6th byte must be 0x4x
            this[6] = this[6] and 0x0F.toByte()
            this[6] = this[6] or 0x40.toByte()

            // Set variant, 8th byte must be 0b10xxxxxx or 0b110xxxxx
            this[8] = this[8] and 0x3F.toByte()
            this[8] = this[8] or 0x80.toByte()
        }
    }

    public constructor(uuidString: String) {
        bytes = parseUUIDString(uuidString)
    }

    public constructor(byteArray: ByteArray) {
        if (byteArray.size != UUID_BYTE_SIZE)
            throw IllegalArgumentException("Invalid 'bytes' size ${byteArray.size}, byte array size must be $UUID_BYTE_SIZE")

        bytes = byteArray
    }

    override fun equals(other: Any?): Boolean {
        // Check if 'other' is null since type coercion would fail in that case
        if (other == null) return false
        return (other as RealmUUID).bytes.contentEquals(bytes)
    }

    override fun hashCode(): Int {
        // We consider two RealmUUID's equal if they have the same byte sequence, so in
        // order to match the contract of equals/hashcode on JVM we calculate the hashcode
        // as the sum of hashcode of all bytes.
        return bytes.contentHashCode()
    }

    override fun toString(): String {
        return bytes.toHexString(0, 4) +
            "-" +
            bytes.toHexString(4, 6) +
            "-" +
            bytes.toHexString(6, 8) +
            "-" +
            bytes.toHexString(8, 10) +
            "-" +
            bytes.toHexString(10, 16)
    }

    public companion object {
        private const val UUID_BYTE_SIZE = 16
        private val UUID_REGEX by lazy {
            ("($HEX_PATTERN{8})-($HEX_PATTERN{4})-($HEX_PATTERN{4})-($HEX_PATTERN{4})-($HEX_PATTERN{12})").toRegex()
        }

        /**
         * Validates and parses an UUID string representation into a byte array.
         */
        private fun parseUUIDString(uuidString: String): ByteArray {
            val matchGroup = UUID_REGEX.matchEntire(uuidString)
                ?: throw IllegalArgumentException("Invalid string representation of an UUID: '$uuidString'")

            val byteGroups = (1..5).map { groupIndex ->
                matchGroup.groups[groupIndex]!!.value.parseHex()
            }

            return byteGroups[0] + byteGroups[1] + byteGroups[2] + byteGroups[3] + byteGroups[4]
        }
    }
}
