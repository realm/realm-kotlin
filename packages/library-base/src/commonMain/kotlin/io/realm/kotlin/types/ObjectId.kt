/*
 * Copyright 2022 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm.kotlin.types

import io.realm.kotlin.internal.ObjectIdImpl

/**
 * A globally unique identifier for objects.
 *
 * Consists of 12 bytes, divided as follows:
 * A 4-byte timestamp, representing the ObjectId's creation, measured in seconds since the Unix epoch.
 * A 5-byte random value generated once per process. This random value is unique to the machine and process.
 * A 3-byte incrementing counter, initialized to a random value.
 */
@Deprecated("Use kbson ObjectId instead", ReplaceWith("org.mongodb.kbson.ObjectId"))
public interface ObjectId : Comparable<ObjectId> {
    public companion object {
        /**
         * Generates a new [ObjectId] using the hexadecimal representation of the 12 bytes.
         *
         * @param hexString the string to convert.
         */
        public fun from(hexString: String): ObjectId = ObjectIdImpl(hexString)

        /**
         * Generates a new [ObjectId] using the provided timestamp.
         *
         * @param date timestamp to be used.
         */
        public fun from(date: RealmInstant): ObjectId = ObjectIdImpl(date)

        /**
         * Generates a new [ObjectId] using the unique 12 bytes representation.
         *
         * @param bytes to use as backing representation
         */
        public fun from(bytes: ByteArray): ObjectId = ObjectIdImpl(bytes)

        /**
         * Generates a new [ObjectId] using default values (current time)
         */
        public fun create(): ObjectId = ObjectIdImpl()
    }
}
