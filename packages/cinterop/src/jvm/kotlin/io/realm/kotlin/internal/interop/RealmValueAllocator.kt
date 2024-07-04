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
@file:Suppress("NOTHING_TO_INLINE")

package io.realm.kotlin.internal.interop

import io.realm.kotlin.internal.interop.RealmInterop.cptr
import org.mongodb.kbson.Decimal128

/**
 * Singleton object as we just rely on GC'ed realm_value_ts and don't keep track of the actual
 * allocations besides that.
 */
@Suppress("OVERRIDE_BY_INLINE")
object JvmMemAllocator : MemAllocator {

    override inline fun allocRealmValueT(): RealmValueT = realm_value_t()
    override inline fun allocRealmValueList(count: Int): RealmValueList = RealmValueList(count, realmc.new_valueArray(count))

    override fun nullTransport(): RealmValue =
        createTransport(null, realm_value_type_e.RLM_TYPE_NULL)

    override fun longTransport(value: Long?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_INT) { integer = it }

    override fun booleanTransport(value: Boolean?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_BOOL) { _boolean = it }

    override fun timestampTransport(value: Timestamp?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_TIMESTAMP) {
            timestamp = realm_timestamp_t().apply {
                seconds = it.seconds
                nanoseconds = it.nanoSeconds
            }
        }

    override fun floatTransport(value: Float?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_FLOAT) { fnum = it }

    override fun doubleTransport(value: Double?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_DOUBLE) { dnum = it }

    override fun decimal128Transport(value: Decimal128?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_DECIMAL128) {
            decimal128 = realm_decimal128_t().apply {
                w = ulongArrayOf(it.low, it.high).toLongArray()
            }
        }

    override fun objectIdTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            object_id = realm_object_id_t().apply {
                val data = ShortArray(OBJECT_ID_BYTES_SIZE)
                (0 until OBJECT_ID_BYTES_SIZE).map { index ->
                    data[index] = it[index].toShort()
                }
                bytes = data
            }
        }

    override fun uuidTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_UUID) {
            uuid = realm_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = it[index].toShort()
                }
                bytes = data
            }
        }

    override fun decimal128Transport(value: ULongArray?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_DECIMAL128) {
            decimal128 = realm_decimal128_t().apply {
                w = it.toLongArray()
            }
        }

    override fun realmObjectTransport(value: RealmObjectInterop?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_LINK) {
            link = realmc.realm_object_as_link(it.objectPointer.cptr())
        }

    private inline fun <T> createTransport(
        value: T?,
        type: Int,
        block: (RealmValueT.(value: T) -> Unit) = {}
    ): RealmValue {
        val struct: realm_value_t = allocRealmValueT()
        struct.type = when (value) {
            null -> realm_value_type_e.RLM_TYPE_NULL
            else -> type
        }
        value?.also { block.invoke(struct, it) }
        return RealmValue(struct)
    }
}

/**
 * Scoped allocator that will ensure that pointers held by realm_value_ts will be freed again when
 * the allocator is cleaned up. Valid for holders of data buffers, i.e. strings and byte arrays.
 */
class JvmMemTrackingAllocator : MemAllocator by JvmMemAllocator, MemTrackingAllocator {

    private val scope = MemScope()

    override fun stringTransport(value: String?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_STRING) {
            string = it
        }

    override fun byteArrayTransport(value: ByteArray?): RealmValue =
        createTransport(value, realm_value_type_e.RLM_TYPE_BINARY) {
            binary = realm_binary_t().apply {
                data = it
                size = it.size.toLong()
            }
        }

    override fun queryArgsOf(queryArgs: List<RealmQueryArgument>): RealmQueryArgumentList {
        val cArgs = realmc.new_queryArgArray(queryArgs.size)
        queryArgs.mapIndexed { index, arg ->
            val queryArg = realm_query_arg_t().apply {
                when (arg) {
                    is RealmQueryListArgument -> {
                        nb_args = arg.arguments.size.toLong()
                        is_list = true
                        this.arg = arg.arguments.head

                        scope.manageQueryListArgument(arg)
                    }
                    is RealmQuerySingleArgument -> {
                        nb_args = 1
                        is_list = false
                        this.arg = arg.argument.value
                    }
                }
            }
            realmc.queryArgArray_setitem(cArgs, index, queryArg)
        }
        return RealmQueryArgumentList(queryArgs.size.toLong(), cArgs).also {
            scope.manageQueryArgumentList(it)
        }
    }

    /**
     * Frees resources linked to this allocator's [scope], more specifically strings and binary
     * buffers. See [MemScope.free] for more details.
     */
    override fun free() = scope.free()

    private inline fun <T> createTransport(
        value: T?,
        type: Int,
        block: (RealmValueT.(value: T) -> Unit) = {}
    ): RealmValue {
        val struct: realm_value_t = allocRealmValueT()
        struct.type = when (value) {
            null -> realm_value_type_e.RLM_TYPE_NULL
            else -> type
        }
        value?.also { block.invoke(struct, it) }
        scope.manageRealmValue(struct)
        return RealmValue(struct)
    }

    /**
     * A factory and container for various resources that can be freed when calling [free].
     *
     * The `managedRealmValue` should be used for all C-API methods that take a realm_value_t as
     * input arguments (contrary to output arguments where the data is managed by the C-API and
     * copied out afterwards).
     */
    class MemScope {
        val values: MutableSet<Any> = mutableSetOf()

        fun manageRealmValue(value: RealmValueT): RealmValueT {
            values.add(value)
            return value
        }

        fun manageQueryArgumentList(value: RealmQueryArgumentList): RealmQueryArgumentList = value.also {
            values.add(value)
        }

        fun manageQueryListArgument(value: RealmQueryListArgument): RealmQueryListArgument = value.also {
            values.add(value)
        }

        fun free() {
            values.forEach {
                when (it) {
                    is RealmValueT -> realmc.realm_value_t_cleanup(it)
                    is RealmQueryArgumentList -> realmc.delete_queryArgArray(it.head)
                    is RealmQueryListArgument -> realmc.delete_valueArray(it.arguments.head)
                }
            }
        }
    }
}

actual inline fun realmValueAllocator(): MemAllocator = JvmMemAllocator
actual inline fun trackingRealmValueAllocator(): MemTrackingAllocator = JvmMemTrackingAllocator()

actual inline fun <R> getterScope(block: MemAllocator.() -> R): R = block(realmValueAllocator())
