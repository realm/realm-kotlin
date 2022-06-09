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

/**
 * A class that represents an immutable universally unique identifier (UUID). A UUID represents a 128-bit value.
 *
 * It does not enforce the standard [RFC4122](https://datatracker.ietf.org/doc/html/rfc4122)
 */
public interface RealmUUID : Comparable<RealmUUID> {
    public companion object {
        /**
         * Generates a new [RealmUUID] with a random UUID value.
         */
        public fun random(): RealmUUID = TODO()

        /**
         * Generates a new [RealmUUID] from an UUID string representation.
         */
        public fun from(uuidString: String): RealmUUID = TODO("validates uuid validity")

        /**
         * Generates a new [RealmUUID] from an UUID byte array representation.
         */
        public fun from(bytes: ByteArray): RealmUUID = TODO("validates uuid validity")
    }

    /**
     * The UUID represented as a 16 byte array
     */
    public val bytes: ByteArray
}
