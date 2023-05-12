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

import org.mongodb.kbson.Decimal128
import kotlin.jvm.JvmName

/**
 * Allocator that handles allocation of C-API structs.
 */
interface MemAllocator {

    /**
     * Allocates a C-API `realm_value_t` struct.
     */
    fun allocRealmValueT(): RealmValueT

    /**
     * Allocates a contiguous list of `realm_value_t` structs.
     */
    fun allocRealmValueList(count: Int): RealmValueList

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` containing `null`.
     */
    // TODO optimize: investigate if we can statically create a null transport and reuse it
    fun nullTransport(): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_INT`.
     */
    fun longTransport(value: Long?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_BOOL`.
     */
    fun booleanTransport(value: Boolean?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_TIMESTAMP`.
     */
    fun timestampTransport(value: Timestamp?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_FLOAT`.
     */
    fun floatTransport(value: Float?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_DOUBLE`.
     */
    fun doubleTransport(value: Double?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_DECIMAL128`.
     */
    fun decimal128Transport(value: Decimal128?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_OBJECT_ID` from
     * an ObjectId's bytes.
     */
    fun objectIdTransport(value: ByteArray?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_UUID` from a
     * RealmUUID's bytes.
     */
    fun uuidTransport(value: ByteArray?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_DECIMAL128`.
     */
    fun decimal128Transport(value: ULongArray?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_LINK`.
     */
    fun realmObjectTransport(value: RealmObjectInterop?): RealmValue

    /**
     * Instantiates a [RealmQueryArgumentList] representing a `realm_query_arg_t` that describe and
     * references the incoming [RealmValueList] arguments.
     */
    fun queryArgsOf(queryArgs: List<RealmQueryArgument>): RealmQueryArgumentList
}

/**
 * Allocator that handles allocation of C-API structs and (potential) deallocation. Deallocation
 * may occur due to the fact that the structs might be managed by the garbage collector e.g. on JVM
 * but the structs should be considered valid outside the scope of the allocator.
 */
interface MemTrackingAllocator : MemAllocator {

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_STRING`.
     */
    fun stringTransport(value: String?): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_BINARY`.
     */
    fun byteArrayTransport(value: ByteArray?): RealmValue

    /**
     * Frees resources linked to this allocator. See implementations for more details.
     */
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

/**
 * Receives a [block] inside which C structs can be allocated for the purpose of retrieving values
 * from the C-API. See each platform-specific implementation for more details on how this is done.
 */
expect inline fun <R> getterScope(block: MemAllocator.() -> R): R

/**
 * Receives a [block] inside which C structs can be allocated for the purpose of sending values
 * to the C-API and whose potential data buffers are cleaned up after completion. See
 * [MemTrackingAllocator.free] for more details.
 */
// TODO optimize: distinguish between tracking and not tracking data buffers - we should avoid
//  leaking the allocators to the internal implementations.
inline fun <R> inputScope(block: MemTrackingAllocator.() -> R): R {
    val allocator = trackingRealmValueAllocator()
    val x = block(allocator)
    allocator.free()
    return x
}
