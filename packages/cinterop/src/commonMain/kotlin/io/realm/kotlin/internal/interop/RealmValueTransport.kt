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

// Allocator that handles allocation and (potential) deallocation. The "potential" deallocation is
// due to the fact that the structs might be managed by the GC ... but the structs should be
// considered valid outside the scope of the allocator
interface RealmValueAllocator {
    fun alloc(): RealmValueT
    fun create(): RealmValueTransport
    fun create(value: Long): RealmValueTransport
    fun create(value: Boolean): RealmValueTransport
    fun create(value: Timestamp): RealmValueTransport
    fun create(value: Float): RealmValueTransport
    fun create(value: Double): RealmValueTransport
    fun create(value: ObjectId): RealmValueTransport
    fun create(value: UUIDWrapper): RealmValueTransport
    fun create(value: Link): RealmValueTransport
}

interface MemTrackingRealmValueAllocator : RealmValueAllocator {
    fun create(value: String): RealmValueTransport
    fun create(value: ByteArray): RealmValueTransport
    // Should be internal!?
    fun free()
}

expect inline fun realmValueAllocator(): RealmValueAllocator
expect inline fun trackingRealmValueAllocator(): MemTrackingRealmValueAllocator
// FIXME This is the old implementation. Kept to make it easy to play around with the concepts, but
//  just mapping to the underlying non-tracking realmValueAllocator
inline fun <R> unscoped(block: (unscopedStruct: RealmValueT) -> R): R = block(realmValueAllocator().alloc())

inline fun <R> scoped(block: RealmValueAllocator.() -> R): R = block(realmValueAllocator())
inline fun <R> scopedTracked(block: MemTrackingRealmValueAllocator.() -> R): R {
    val allocator = trackingRealmValueAllocator()
    val x = block(allocator)
    allocator.free()
    return x
}

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
    companion object {
        operator fun MemTrackingRealmValueAllocator.invoke(
            queryArgs: Array<RealmValueTransport>
        ): RealmQueryArgsTransport
    }
}
