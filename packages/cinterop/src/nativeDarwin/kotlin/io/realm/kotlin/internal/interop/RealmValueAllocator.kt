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

@file:Suppress("OVERRIDE_BY_INLINE")

package io.realm.kotlin.internal.interop

import kotlinx.cinterop.Arena
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import org.mongodb.kbson.Decimal128
import platform.posix.memcpy
import realm_wrapper.realm_query_arg_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

/**
 * Only one allocator is needed for K/N as structs will be freed after completion no matter what,
 * which includes their buffers.
 */
class NativeMemAllocator : MemTrackingAllocator {

    val scope = Arena()

    @Suppress("NOTHING_TO_INLINE")
    override inline fun allocRealmValueT(): RealmValueT = scope.alloc()

    @Suppress("NOTHING_TO_INLINE")
    override inline fun allocRealmValueList(count: Int): RealmValueList =
        RealmValueList(count, scope.allocArray(count))

    override fun nullTransport(): RealmValue =
        createTransport(null, realm_value_type.RLM_TYPE_NULL)

    override fun longTransport(value: Long?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_INT) { integer = it }

    override fun booleanTransport(value: Boolean?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_BOOL) { boolean = it }

    override fun timestampTransport(value: Timestamp?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_TIMESTAMP) {
            timestamp.apply {
                seconds = it.seconds
                nanoseconds = it.nanoSeconds
            }
        }

    override fun floatTransport(value: Float?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_FLOAT) { fnum = it }

    override fun doubleTransport(value: Double?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_DOUBLE) { dnum = it }

    override fun decimal128Transport(value: Decimal128?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_DECIMAL128) {
            decimal128.apply {
                w[0] = it.low
                w[1] = it.high
            }
        }

    override fun objectIdTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_OBJECT_ID) {
            object_id.apply {
                // TODO BENCHMARK: is this faster than memcpy? see 'uuidTransport'
                (0 until OBJECT_ID_BYTES_SIZE).map { index ->
                    bytes[index] = it[index].toUByte()
                }
            }
        }

    override fun uuidTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_UUID) {
            uuid.apply {
                // TODO BENCHMARK: is this faster than looping through the structure? see 'objectIdTransport'
                it.usePinned {
                    val dest = bytes.getPointer(scope)
                    val source = it.addressOf(0)
                    memcpy(dest, source, UUID_BYTES_SIZE.toULong())
                }
            }
        }

    override fun decimal128Transport(value: ULongArray?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_DECIMAL128) {
            decimal128.apply {
                it.usePinned { pinnedValue ->
                    val dest = w.getPointer(scope)
                    val source = pinnedValue.addressOf(0)
                    memcpy(dest, source, 2.toULong())
                }
            }
        }

    override fun realmObjectTransport(value: RealmObjectInterop?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_LINK) {
            realm_wrapper.realm_object_as_link(it.objectPointer.cptr())
                .useContents {
                    link.apply {
                        target_table = this@useContents.target_table
                        target = this@useContents.target
                    }
                }
        }

    override fun stringTransport(value: String?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_STRING) { string.set(scope, it) }

    override fun byteArrayTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type.RLM_TYPE_BINARY) { binary.set(scope, it) }

    override fun queryArgsOf(queryArgs: List<RealmQueryArgument>): RealmQueryArgumentList {
        val cArgs = scope.allocArray<realm_query_arg_t>(queryArgs.size)
        queryArgs.mapIndexed { i, arg ->
            cArgs[i].apply {
                when (arg) {
                    is RealmQueryListArgument -> {
                        nb_args = arg.arguments.size.toULong()
                        is_list = true
                        this.arg = arg.arguments.head
                    }

                    is RealmQuerySingleArgument -> {
                        nb_args = 1U
                        is_list = false
                        this.arg = arg.argument.value.ptr
                    }
                }
            }
        }
        return RealmQueryArgumentList(queryArgs.size.toULong(), cArgs.pointed)
    }

    override fun free() {
        scope.clear()
    }

    private inline fun <T> createTransport(
        value: T?,
        type: realm_value_type,
        block: (RealmValueT.(value: T) -> Unit) = {},
    ): RealmValue {
        val struct: realm_value_t = allocRealmValueT()
        struct.type = when (value) {
            null -> realm_value_type.RLM_TYPE_NULL
            else -> type
        }
        value?.also { block.invoke(struct, it) }
        return RealmValue(struct)
    }
}

/**
 * We always need to track and free native resources in K/N so all allocators return a
 * [MemTrackingAllocator].
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun realmValueAllocator(): MemAllocator = NativeMemAllocator()

/**
 * We always need to track and free native resources in K/N so all allocators return a
 * [MemTrackingAllocator].
 */
@Suppress("NOTHING_TO_INLINE")
actual inline fun trackingRealmValueAllocator(): MemTrackingAllocator = NativeMemAllocator()

/**
 * We always need to work on a scope that frees resources after completion in K/N. That is why we
 * always call `inputScope` in this implementation regardless of whether we are reading or storing
 * values.
 */
actual inline fun <R> getterScope(block: MemAllocator.() -> R): R = inputScope(block)
