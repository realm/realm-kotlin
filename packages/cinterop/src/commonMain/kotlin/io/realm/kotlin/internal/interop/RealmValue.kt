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

/**
 * Representation of a C-API `realm_value_t` struct.
 */
expect class RealmValueT

/**
 * Inline class used for handling C-API `realm_value_t` structs. It behaves exactly like the struct.
 */
expect value class RealmValue(val value: RealmValueT) {

    // TODO should we consider scoping these functions to an allocator?
    inline fun getType(): ValueType

    inline fun getLong(): Long
    inline fun getBoolean(): Boolean
    inline fun getString(): String
    inline fun getByteArray(): ByteArray
    inline fun getTimestamp(): Timestamp
    inline fun getFloat(): Float
    inline fun getDouble(): Double
    inline fun getObjectIdBytes(): ByteArray
    inline fun getUUIDBytes(): ByteArray
    inline fun getDecimal128(): ULongArray
    inline fun getLink(): Link
}

/**
 * Representation of a C-API `realm_query_arg_t` struct.
 */
expect class RealmQueryArgT

/**
 * Inline class used for handling C-API `realm_query_arg_t` structs used when building queries.
 */
expect value class RealmQueryArgsTransport(val value: RealmQueryArgT)

/**
 * Checks whether the transport object is `RLM_TYPE_NULL`.
 */
expect fun RealmValue.isNull(): Boolean
