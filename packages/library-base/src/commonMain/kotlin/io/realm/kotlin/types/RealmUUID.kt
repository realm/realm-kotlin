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
 */
public interface RealmUUID {
    public companion object {
        /**
         * Generates a new [RealmUUID] type 4 (pseudo randomly generated) UUID.
         */
        public fun random(): RealmUUID = RealmUUIDImpl()

        /**
         * Generates a new [RealmUUID] from the UUID formatted string.
         */
        public fun from(uuidString: String): RealmUUID = RealmUUIDImpl(uuidString)

        /**
         * Generates a new [RealmUUID] based on the specified byte array.
         */
        public fun from(bytes: ByteArray): RealmUUID = RealmUUIDImpl(bytes)
    }

    /**
     * The UUID represented as a 16 byte array.
     */
    public val bytes: ByteArray
}
