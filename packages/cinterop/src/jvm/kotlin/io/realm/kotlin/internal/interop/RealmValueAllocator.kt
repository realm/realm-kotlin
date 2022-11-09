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

/**
 * Singleton object as we just rely on GC'ed realm_value_ts and don't keep track of the actual
 * allocations besides that.
 */
@Suppress("OVERRIDE_BY_INLINE")
object JvmMemAllocator : MemAllocator {

    override inline fun allocRealmValueT(): RealmValueT = realm_value_t()

    override fun transportOf(): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_NULL)

    override fun transportOf(value: Long): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_INT) { integer = value }

    override fun transportOf(value: Boolean): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_BOOL) { _boolean = value }

    override fun transportOf(value: Timestamp): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_TIMESTAMP) {
            timestamp = realm_timestamp_t().apply {
                seconds = value.seconds
                nanoseconds = value.nanoSeconds
            }
        }

    override fun transportOf(value: Float): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_FLOAT) { fnum = value }

    override fun transportOf(value: Double): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_DOUBLE) { dnum = value }

    override fun transportOf(value: ObjectId): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            object_id = value.asRealmObjectIdT()
        }

    override fun transportOf(value: UUIDWrapper): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_UUID) {
            uuid = realm_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = value.bytes[index].toShort()
                }
                bytes = data
            }
        }

    override fun transportOf(value: Link): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_LINK) {
            link = realm_link_t().apply {
                target_table = value.classKey.key
                target = value.objKey
            }
        }

    override fun queryArgsOf(queryArgs: Array<RealmValue>): RealmQueryArgsTransport {
        val cArgs = realmc.new_queryArgArray(queryArgs.size)
        queryArgs.forEachIndexed { index, realmValueTransport ->
            realm_query_arg_t().apply {
                this.nb_args = 1
                this.is_list = false
                this.arg = realmValueTransport.value
            }.also { queryArg: realm_query_arg_t ->
                realmc.queryArgArray_setitem(cArgs, index, queryArg)
            }
        }
        return RealmQueryArgsTransport(cArgs)
    }

    private inline fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit) = {}
    ): RealmValue {
        val struct: realm_value_t = allocRealmValueT()
        struct.type = type
        block.invoke(struct)
        return RealmValue(struct)
    }
}

/**
 * Scoped allocator that will ensure that pointers held by realm_value_ts will be freed again when
 * the allocator is cleaned up. Valid for holders of data buffers, i.e. strings and byte arrays.
 */
class JvmMemTrackingAllocator : MemAllocator by JvmMemAllocator, MemTrackingAllocator {

    private val scope = MemScope()

    override fun transportOf(value: String): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_STRING) {
            string = value
        }

    override fun transportOf(value: ByteArray): RealmValue =
        createTransport(realm_value_type_e.RLM_TYPE_BINARY) {
            binary = realm_binary_t().apply {
                data = value
                size = value.size.toLong()
            }
        }

    /**
     * Frees resources linked to this allocator's [scope], more specifically strings and binary
     * buffers. See [MemScope.free] for more details.
     */
    override fun free() = scope.free()

    private inline fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit) = {}
    ): RealmValue {
        val cValue: realm_value_t = allocRealmValueT()
        cValue.type = type
        block.invoke(cValue)
        scope.manageRealmValue(cValue)
        return RealmValue(cValue)
    }

    /**
     * A factory and container for various resources that can be freed when calling [free].
     *
     * The `managedRealmValue` should be used for all C-API methods that take a realm_value_t as
     * input arguments (contrary to output arguments where the data is managed by the C-API and
     * copied out afterwards).
     */
    class MemScope {
        val values: MutableSet<RealmValueT> = mutableSetOf()

        fun manageRealmValue(value: RealmValueT): RealmValueT {
            values.add(value)
            return value
        }

        fun free() {
            values.map {
                realmc.realm_value_t_cleanup(it)
            }
        }
    }
}

actual inline fun realmValueAllocator(): MemAllocator = JvmMemAllocator
actual inline fun trackingRealmValueAllocator(): MemTrackingAllocator = JvmMemTrackingAllocator()

actual inline fun <R> getterScope(block: MemAllocator.() -> R): R =
    block(realmValueAllocator())

actual inline fun <R> setterScope(block: MemAllocator.() -> R): R =
    block(realmValueAllocator())
