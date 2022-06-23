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

package io.realm.kotlin.test.shared

import io.realm.kotlin.test.assertFailsWithMessage
import io.realm.kotlin.types.RealmUUID
import kotlin.experimental.and
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

class RealmUUIDTests {
    @Test
    fun from_uuidString() {
        // empty string
        assertFailsWithMessage<IllegalArgumentException>("Invalid string representation of an UUID: ''") {
            RealmUUID.from("") // empty string
        }

        // invalid hex values
        assertFailsWithMessage<IllegalArgumentException>("Invalid string representation of an UUID: '893062aa-3207-49ad-xxxx-8db653771cdb'") {
            RealmUUID.from("893062aa-3207-49ad-xxxx-8db653771cdb") // invalid uuid value
        }

        // No hyphens
        assertFailsWithMessage<IllegalArgumentException>("Invalid string representation of an UUID: '893062aa320749ad931c8db653771cdb'") {
            RealmUUID.from("893062aa320749ad931c8db653771cdb") // invalid uuid value
        }

        // validate regex boundaries
        assertFailsWithMessage<IllegalArgumentException>("Invalid string representation of an UUID: '00000000-0000-0000-0000-000000000000   '") {
            RealmUUID.from(
                """
                00000000-0000-0000-0000-000000000000   
                """.trimIndent()
            ) // invalid uuid value
        }

        // validate regex boundaries
        assertFailsWithMessage<IllegalArgumentException>("Invalid string representation of an UUID: '00000000-0000-0000-0000-00000000000000000000-0000-0000-0000-000000000000'") {
            RealmUUID.from("00000000-0000-0000-0000-00000000000000000000-0000-0000-0000-000000000000") // invalid uuid value
        }

        // Boundaries
        assertContentEquals(
            ByteArray(16) { 0x00.toByte() },
            RealmUUID.from("00000000-0000-0000-0000-000000000000").bytes
        )

        assertContentEquals(
            ByteArray(16) { 0xFF.toByte() },
            RealmUUID.from("ffffffff-ffff-ffff-ffff-ffffffffffff").bytes
        )

        assertContentEquals(
            ByteArray(16) { 0xFF.toByte() },
            RealmUUID.from("FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF").bytes
        )
    }

    @Test
    fun from_bytes() {
        // empty array
        assertFailsWithMessage<IllegalArgumentException>("Invalid 'bytes' size 0, byte array size must be 16") {
            RealmUUID.from(byteArrayOf()) // 16 char needed
        }

        // too small array
        assertFailsWithMessage<IllegalArgumentException>("Invalid 'bytes' size 6, byte array size must be 16") {
            RealmUUID.from(ByteArray(6) { 0x00 })
        }

        // too large array
        assertFailsWithMessage<IllegalArgumentException>("Invalid 'bytes' size 20, byte array size must be 16") {
            RealmUUID.from(ByteArray(20) { 0x00 })
        }

        // Boundaries
        assertContentEquals(
            ByteArray(16) { 0x00.toByte() },
            RealmUUID.from(ByteArray(16) { 0x00.toByte() }).bytes,
        )

        assertContentEquals(
            ByteArray(16) { 0xFF.toByte() },
            RealmUUID.from(ByteArray(16) { 0xFF.toByte() }).bytes,
        )
    }

    @Test
    fun random() {
        val uuidVersion4 = 0x40.toByte()
        val version4Variants = byteArrayOf((0b10 shl 6).toByte(), (0b11 shl 6).toByte())

        // Try with different random values
        repeat(10) {
            // validate version, 6th byte must be 0x40
            assertEquals(uuidVersion4, RealmUUID.random().bytes[6] and 0xF0.toByte())

            // validate variant, 8th byte must be 0b10xxxxxx or 0b110xxxxx
            assertContains(version4Variants, RealmUUID.random().bytes[8] and 0xC0.toByte())

            // it yields different values
            val uuid1 = RealmUUID.random()
            val uuid2 = RealmUUID.random()

            assertFalse(uuid1.bytes.contentEquals(uuid2.bytes))
            assertNotEquals(uuid1, uuid2)
        }
    }

    @Test
    fun to_String() {
        repeat(10) {
            val uuid = RealmUUID.random()
            val copy = RealmUUID.from(uuid.toString())

            assertEquals(uuid, copy)
        }
    }
}
