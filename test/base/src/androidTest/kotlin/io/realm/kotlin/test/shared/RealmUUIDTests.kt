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
import kotlin.test.Test
import kotlin.test.assertContentEquals

class RealmUUIDTests {
    @Test
    fun from_uuidString() {
        // Invalid arguments
        assertFailsWithMessage<IllegalArgumentException>("invalid string representation of an UUID: ''") {
            RealmUUID.from("") // empty string
        }

        assertFailsWithMessage<IllegalArgumentException>("invalid string representation of an UUID: 'ffffffff-ffff-xxxx-ffff-ffffffffffff'") {
            RealmUUID.from("ffffffff-ffff-xxxx-ffff-ffffffffffff") // invalid uuid value
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

        // Values
    }

    @Test
    fun from_bytes() {
        // Invalid arguments
        assertFailsWithMessage<IllegalArgumentException>("byte array size must be 16") {
            RealmUUID.from(byteArrayOf()) // 16 char needed
        }

        assertFailsWithMessage<IllegalArgumentException>("byte array size too small, size must be 16") {
            RealmUUID.from(ByteArray(6) { 0x00 })
        }

        assertFailsWithMessage<IllegalArgumentException>("byte array size too small, size must be 16") {
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

        // Some values
    }

    @Test
    fun random() {
        // It yields different values
        assertContentNotEquals(
            RealmUUID.random(),
            RealmUUID.random()
        )
    }

    @Test
    fun compare() {
        // equals

        // less than

        // greater than
    }

    @Test
    fun to_String() {
        // roundtrip
    }
}
