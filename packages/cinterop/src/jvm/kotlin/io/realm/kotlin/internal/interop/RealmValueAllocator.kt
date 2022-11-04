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
object JvmMemAllocator : MemAllocator {

    override fun allocRealmValueT(): RealmValueT = realm_value_t()

    override fun transportOf(): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_NULL)

    override fun transportOf(value: Long): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_INT) { integer = value }

    override fun transportOf(value: Boolean): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_BOOL) { _boolean = value }

    override fun transportOf(value: Timestamp): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_TIMESTAMP) {
            timestamp = realm_timestamp_t().apply {
                seconds = value.seconds
                nanoseconds = value.nanoSeconds
            }
        }

    override fun transportOf(value: Float): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_FLOAT) { fnum = value }

    override fun transportOf(value: Double): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_DOUBLE) { dnum = value }

    override fun transportOf(value: ObjectId): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            object_id = value.asRealmObjectIdT()
        }

    override fun transportOf(value: UUIDWrapper): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_UUID) {
            uuid = realm_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = value.bytes[index].toShort()
                }
                bytes = data
            }
        }

    override fun transportOf(value: Link): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_LINK) {
            link = realm_link_t().apply {
                target_table = value.classKey.key
                target = value.objKey
            }
        }

    override fun queryArgsOf(queryArgs: Array<RealmValueTransport>): RealmQueryArgsTransport {
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

    private fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit)? = null
    ): RealmValueTransport {
        val struct: realm_value_t = allocRealmValueT()
        struct.type = type
        block?.invoke(struct)
        return RealmValueTransport(struct)
    }
}

/**
 * Scoped allocator that will ensure that pointers held by realm_value_ts will be freed again when
 * the allocator is cleaned up. Valid for holders of data buffers, i.e. strings and byte arrays.
 */
class JvmMemTrackingAllocator : MemAllocator by JvmMemAllocator, MemTrackingAllocator {

    private val scope = MemScope()

    override fun transportOf(value: String): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_STRING) {
            string = value
        }

    override fun transportOf(value: ByteArray): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_BINARY) {
            binary = realm_binary_t().apply {
                data = value
                size = value.size.toLong()
            }
        }

    override fun free() = scope.free()

    private fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit)? = null
    ): RealmValueTransport {
        val cValue: realm_value_t = allocRealmValueT()
        cValue.type = type
        block?.invoke(cValue)
        scope.manageRealmValue(cValue)
        return RealmValueTransport(cValue)
    }
}

actual inline fun realmValueAllocator(): MemAllocator = JvmMemAllocator
actual inline fun trackingRealmValueAllocator(): MemTrackingAllocator = JvmMemTrackingAllocator()

actual inline fun <R> getterScope(block: MemAllocator.() -> R): R =
    block(realmValueAllocator())

actual inline fun <R> setterScope(block: MemAllocator.() -> R): R =
    block(realmValueAllocator())
