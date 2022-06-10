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

import io.realm.kotlin.types.RealmUUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class RealmUUIDTests {
    @Test
    fun from_uuidString() {
        // empty string
        assertFailsWith<IllegalArgumentException>("invalid string representation of an UUID: ''") {
            RealmUUID.from("") // empty string
        }

        // invalid hex values
        assertFailsWith<IllegalArgumentException>("invalid string representation of an UUID: '893062aa-3207-49ad-xxxx-8db653771cdb'") {
            RealmUUID.from("893062aa-3207-49ad-xxxx-8db653771cdb") // invalid uuid value
        }

        // No hyphens
        assertFailsWith<IllegalArgumentException>("invalid string representation of an UUID: '893062aa320749ad931c8db653771cdb'") {
            RealmUUID.from("893062aa320749ad931c8db653771cdb") // invalid uuid value
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

        // values
    }

    @Test
    fun from_bytes() {
        // empty array
        assertFailsWith<IllegalArgumentException>("byte array size must be 16") {
            RealmUUID.from(byteArrayOf()) // 16 char needed
        }

        // too small array
        assertFailsWith<IllegalArgumentException>("byte array size too small, size must be 16") {
            RealmUUID.from(ByteArray(6) { 0x00 })
        }

        // too large array
        assertFailsWith<IllegalArgumentException>("byte array size too small, size must be 16") {
            RealmUUID.from(ByteArray(20) { 0x00 })
        }

        // Boundaries
        assertContentEquals(
            RealmUUID.from(ByteArray(16) { 0x00.toByte() }).bytes,
            ByteArray(16) { 0x00.toByte() },
        )

        assertContentEquals(
            RealmUUID.from(ByteArray(16) { 0xFF.toByte() }).bytes,
            ByteArray(16) { 0xFF.toByte() }
        )

        // some values
    }

    @Test
    fun random() {
        // Try with different random values

        repeat(10) {
            // are valid v4
            assertEquals(4, RealmUUID.random().bytes[12])

            // it yields different values
            assertNotEquals(RealmUUID.random(), RealmUUID.random())
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
