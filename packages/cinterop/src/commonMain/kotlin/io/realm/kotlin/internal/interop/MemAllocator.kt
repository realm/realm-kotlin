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

@file:JvmName("RealmValueAllocatorJvm")

package io.realm.kotlin.internal.interop

import org.mongodb.kbson.ObjectId
import kotlin.jvm.JvmName

/**
 * Allocator that handles allocation and (potential) deallocation. The "potential" deallocation is
 * due to the fact that the structs might be managed by the GC (e.g. on JVM) but the structs should
 * be considered valid outside the scope of the allocator.
 */
interface MemAllocator {
    fun allocRealmValueT(): RealmValueT
    fun transportOf(): RealmValueTransport
    fun transportOf(value: Long): RealmValueTransport
    fun transportOf(value: Boolean): RealmValueTransport
    fun transportOf(value: Timestamp): RealmValueTransport
    fun transportOf(value: Float): RealmValueTransport
    fun transportOf(value: Double): RealmValueTransport
    fun transportOf(value: ObjectId): RealmValueTransport
    fun transportOf(value: UUIDWrapper): RealmValueTransport
    fun transportOf(value: Link): RealmValueTransport
    fun queryArgsOf(queryArgs: Array<RealmValueTransport>): RealmQueryArgsTransport
}

/**
 * TODO
 */
interface MemTrackingAllocator : MemAllocator {
    fun transportOf(value: String): RealmValueTransport
    fun transportOf(value: ByteArray): RealmValueTransport
    fun free() // TODO not possible to make it internal here but we could create extension functions for each platform?
}

/**
 * Creates an allocator that does **not** cleanup buffers upon completion.
 */
expect inline fun realmValueAllocator(): MemAllocator

/**
 * Creates an allocator that **does** cleanup buffers upon completion.
 */
expect inline fun trackingRealmValueAllocator(): MemTrackingAllocator

// FIXME This is the old implementation. Kept to make it easy to play around with the concepts, but
//  just mapping to the underlying non-tracking realmValueAllocator
// TODO convert it to 'expect' or else K/N won't do the cleanup after completion
expect inline fun <R> getterScope(block: MemAllocator.() -> R): R
expect inline fun <R> setterScope(block: MemAllocator.() -> R): R

//inline fun <R> getterScope(block: RealmValueAllocator.() -> R): R = block(realmValueAllocator())
//inline fun <R> setterScope(block: RealmValueAllocator.() -> R): R = block(realmValueAllocator())
inline fun <R> setterScopeTracked(block: MemTrackingAllocator.() -> R): R {
    val allocator = trackingRealmValueAllocator()
    val x = block(allocator)
    allocator.free()
    return x
}
