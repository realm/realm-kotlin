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
 * Allocator that handles allocation of C-API structs.
 */
interface MemAllocator {

    /**
     * Allocates a C-API `realm_value_t` struct.
     */
    fun allocRealmValueT(): RealmValueT

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` containing `null`.
     */
    fun transportOf(): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_INT`.
     */
    fun transportOf(value: Long): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_BOOL`.
     */
    fun transportOf(value: Boolean): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_TIMESTAMP`.
     */
    fun transportOf(value: Timestamp): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_FLOAT`.
     */
    fun transportOf(value: Float): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_TIMESTAMP`.
     */
    fun transportOf(value: Double): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_OBJECT_ID`.
     */
    fun transportOf(value: ObjectId): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_UUID`.
     */
    fun transportOf(value: UUIDWrapper): RealmValue

    /**
     * Instantiates a [RealmValue] representing a `realm_value_t` of type `RLM_TYPE_LINK`.
     */
    fun transportOf(value: RealmObjectInterop): RealmValue

    /**
     * Instantiates a [RealmQueryArgsTransport] representing a `realm_query_arg_t` which in turn
     * contains one or more `realm_value_t` as arguments.
     */
    fun queryArgsOf(queryArgs: Array<RealmValue>): RealmQueryArgsTransport
}

/**
 * Allocator that handles allocation of C-API structs and (potential) deallocation. Deallocation
 * may occur due to the fact that the structs might be managed by the garbage collector e.g. on JVM
 * but the structs should be considered valid outside the scope of the allocator.
 */
interface MemTrackingAllocator : MemAllocator {
    fun transportOf(value: String): RealmValue
    fun transportOf(value: ByteArray): RealmValue

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
 * that need no cleanup to the C-API. See each platform-specific implementation for more details on
 * how this is done.
 */
expect inline fun <R> setterScope(block: MemAllocator.() -> R): R

/**
 * Receives a [block] inside which C structs can be allocated for the purpose of sending values
 * to the C-API and whose potential data buffers are cleaned up after completion. See
 * [MemTrackingAllocator.free] for more details.
 */
inline fun <R> setterScopeTracked(block: MemTrackingAllocator.() -> R): R {
    val allocator = trackingRealmValueAllocator()
    val x = block(allocator)
    allocator.free()
    return x
}
