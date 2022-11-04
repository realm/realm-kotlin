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

import org.mongodb.kbson.ObjectId

expect class RealmValueT

expect value class RealmValueTransport(val value: RealmValueT) {

    // FIXME Should we consider to make all these methods scoped to a RealmValueAllocator receiver
    //  so that we cannot operate on the RealmValueTransports outside the Allocator scope!?
    inline fun getType(): ValueType

    inline fun getLong(): Long
    inline fun getBoolean(): Boolean
    inline fun getString(): String
    inline fun getByteArray(): ByteArray
    inline fun getTimestamp(): Timestamp
    inline fun getFloat(): Float
    inline fun getDouble(): Double
    inline fun getObjectId(): ObjectId
    inline fun getUUIDWrapper(): UUIDWrapper
    inline fun getLink(): Link

    inline fun <reified T> get(): T
}

expect class RealmQueryArgT

expect value class RealmQueryArgsTransport(val value: RealmQueryArgT) {
//    companion object {
//        fun create(queryArgs: Array<RealmValueTransport>): RealmQueryArgsTransport
//    }
}
