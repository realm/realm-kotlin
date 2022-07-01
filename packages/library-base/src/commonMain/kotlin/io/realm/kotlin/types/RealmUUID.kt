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

package io.realm.kotlin.types

import io.realm.kotlin.internal.RealmUUIDImpl

/**
 * A class that represents an immutable universally unique identifier (UUID). A UUID represents a 128-bit value.
 *
 * UUIDs created with RealmUUID conforms to RFC 4122 version 4 and are created with random bytes.
 */
public interface RealmUUID {
    public companion object {
        /**
         * Generates a new [RealmUUID] type 4 (pseudo randomly generated) UUID.
         *
         * @return A randomly generated UUID
         */
        public fun random(): RealmUUID = RealmUUIDImpl()

        /**
         * Generates a new [RealmUUID] from the UUID formatted string. UUID are represented as
         * 32 hexadecimal (base-16) digits, displayed in five groups separated by hyphens, in the
         * form 8-4-4-4-12 for a total of 36 characters (32 hexadecimal characters and 4 hyphens).
         *
         * @param uuidString A string that specifies a UUID
         * @return A UUID with the specified value
         *
         * @throws IllegalArgumentException if [uuidString] does not match the UUID string format.
         */
        public fun from(uuidString: String): RealmUUID = RealmUUIDImpl(uuidString)

        /**
         * Generates a new [RealmUUID] based on the specified byte array. A valid UUID is represented
         * by a byte array of size 16.
         *
         * @param bytes A byte array that specifies a UUID
         * @return A UUID with the specified value
         *
         * @throws IllegalArgumentException if [bytes] does not match the required byte array size 16.
         */
        public fun from(bytes: ByteArray): RealmUUID = RealmUUIDImpl(bytes)
    }

    /**
     * The UUID represented as a 16 byte array.
     */
    public val bytes: ByteArray

    /**
     * Returns a string that represents the UUID. UUID are represented as
     * 32 hexadecimal (base-16) digits, displayed in five groups separated by hyphens, in the
     * form 8-4-4-4-12 for a total of 36 characters (32 hexadecimal characters and 4 hyphens).
     *
     * @return uuidString A string that represents an UUID
     */
    public override fun toString(): String

    /**
     * Two UUIDs are equal if they contain the same value, bit for bit.
     *
     * @return uuidString A string that represents an UUID
     */
    public override fun equals(other: Any?): Boolean
}
