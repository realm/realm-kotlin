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

import kotlinx.cinterop.Arena
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import org.mongodb.kbson.ObjectId
import platform.posix.memcpy
import realm_wrapper.realm_query_arg_t
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

/**
 * Only one allocator is needed for K/N as structs will be freed after completion no matter what
 * which includes their buffers.
 */
class NativeMemAllocator : MemTrackingAllocator {

    private val scope = Arena()

    override fun allocRealmValueT(): RealmValueT = scope.alloc()

    override fun transportOf(): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_NULL)

    override fun transportOf(value: Long): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_INT) {
            integer = value
        }

    override fun transportOf(value: Boolean): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_BOOL) {
            boolean = value
        }

    override fun transportOf(value: Timestamp): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_TIMESTAMP) {
            timestamp.apply {
                seconds = value.seconds
                nanoseconds = value.nanoSeconds
            }
        }

    override fun transportOf(value: Float): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_FLOAT) {
            fnum = value
        }

    override fun transportOf(value: Double): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_DOUBLE) {
            dnum = value
        }

    override fun transportOf(value: ObjectId): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_OBJECT_ID) {
            object_id.apply {
                val objectIdBytes = value.toByteArray()
                (0 until OBJECT_ID_BYTES_SIZE).map {
                    bytes[it] = objectIdBytes[it].toUByte()
                }
            }
        }

    override fun transportOf(value: UUIDWrapper): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_UUID) {
            uuid.apply {
                value.bytes.usePinned {
                    val dest = bytes.getPointer(scope)
                    val source = it.addressOf(0)
                    memcpy(dest, source, UUID_BYTES_SIZE.toULong())
                }
            }
        }

    override fun transportOf(value: Link): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_LINK) {
            link.apply {
                target_table = value.classKey.key.toUInt()
                target = value.objKey
            }
        }

    override fun transportOf(value: String): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_STRING) {
            string.set(scope, value)
        }

    override fun transportOf(value: ByteArray): RealmValueTransport =
        createTransport(realm_value_type.RLM_TYPE_BINARY) {
            binary.set(scope, value)
        }

    override fun queryArgsOf(queryArgs: Array<RealmValueTransport>): RealmQueryArgsTransport {
        val cArgs = scope.allocArray<realm_query_arg_t>(queryArgs.size)
        queryArgs.mapIndexed { i, arg ->
            cArgs[i].apply {
                this.nb_args = 1.toULong()
                this.is_list = false
                this.arg = arg.value.ptr
            }
        }
        return RealmQueryArgsTransport(cArgs.pointed)
    }

    override fun free() {
        scope.clear()
    }

    private fun createTransport(
        type: realm_value_type,
        block: (RealmValueT.() -> Unit)? = null
    ): RealmValueTransport {
        val cValue: realm_value_t = allocRealmValueT()
        cValue.type = type
        block?.invoke(cValue)
        return RealmValueTransport(cValue)
    }
}

actual inline fun realmValueAllocator(): MemAllocator = NativeMemAllocator()
actual inline fun trackingRealmValueAllocator(): MemTrackingAllocator =
    NativeMemAllocator()

actual inline fun <R> getterScope(block: MemAllocator.() -> R): R = setterScopeTracked(block)

actual inline fun <R> setterScope(block: MemAllocator.() -> R): R = setterScopeTracked(block)
