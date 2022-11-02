@file:JvmName("SomethingUnique")
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

actual typealias RealmValueT = realm_value_t

// Singleton object as we just rely on GC'ed realm_value_ts and don't keep track of the actual
// allocations besides that
object jvmRealmValueAllocator : RealmValueAllocator {
    private fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit)? = null
    ): RealmValueTransport {
        val cValue: realm_value_t = alloc()
        cValue.type = type
        block?.invoke(cValue)
        return RealmValueTransport(cValue)
    }

    override fun alloc(): RealmValueT = realm_value_t()
    override fun create(): RealmValueTransport = createTransport(realm_value_type_e.RLM_TYPE_NULL)
    override fun create(value: Long): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_INT) { integer = value }

    override fun create(value: Boolean): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_BOOL) { _boolean = value }

    override fun create(value: Timestamp): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_TIMESTAMP) {
            timestamp = realm_timestamp_t().apply {
                seconds = value.seconds
                nanoseconds = value.nanoSeconds
            }
        }

    override fun create(value: Float): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_FLOAT) { fnum = value }

    override fun create(value: Double): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_DOUBLE) { dnum = value }

    override fun create(value: ObjectId): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            object_id = value.asRealmObjectIdT()
        }

    override fun create(value: UUIDWrapper): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_UUID) {
            uuid = realm_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = value.bytes[index].toShort()
                }
                bytes = data
            }
        }

    override fun create(value: Link): RealmValueTransport =
        createTransport(realm_value_type_e.RLM_TYPE_LINK) {
            this.link = realm_link_t().apply {
                target_table = value.classKey.key
                target = value.objKey
            }
        }
}

// Scoped allocator that will ensure that pointers held by realm_value_ts will be freed again when
// the allocator is cleaned up
class ScopedAllocator : MemTrackingRealmValueAllocator, RealmValueAllocator by jvmRealmValueAllocator {
    val scope = MemScope()
    private fun createTransport(
        type: Int,
        block: (RealmValueT.() -> Unit)? = null
    ): RealmValueTransport {
        val cValue: realm_value_t = alloc()
        cValue.type = type
        block?.invoke(cValue)
        scope.manageRealmValue(cValue)
        return RealmValueTransport(cValue)
    }
    override fun create(value: String): RealmValueTransport = createTransport(realm_value_type_e.RLM_TYPE_STRING) {
        string = value
    }
    override fun create(value: ByteArray): RealmValueTransport = createTransport(realm_value_type_e.RLM_TYPE_BINARY) {
            binary = realm_binary_t().apply {
                data = value
                size = value.size.toLong()
            }
        }

    override fun free() = scope.free()
}

internal actual inline fun realmValueAllocator(): RealmValueAllocator = jvmRealmValueAllocator
internal actual inline fun trackingRealmValueAllocator(): MemTrackingRealmValueAllocator = ScopedAllocator()

@JvmInline
actual value class RealmValueTransport actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getLong(): Long = value.integer
    actual inline fun getBoolean(): Boolean = value._boolean
    actual inline fun getString(): String = value.string
    actual inline fun getByteArray(): ByteArray = value.binary.data
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()
    actual inline fun getFloat(): Float = value.fnum
    actual inline fun getDouble(): Double = value.dnum
    actual inline fun getObjectId(): ObjectId = value.asObjectId()
    actual inline fun getUUIDWrapper(): UUIDWrapper = value.asUUID()
    actual inline fun getLink(): Link = value.asLink()

    @Suppress("ComplexMethod")
    actual inline fun <reified T> get(): T {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val result = when (T::class) {
            Int::class -> value.integer.toInt()
            Short::class -> value.integer.toShort()
            Long::class -> value.integer
            Byte::class -> value.integer.toByte()
            Char::class -> value.integer.toInt().toChar()
            Boolean::class -> value._boolean
            String::class -> value.string
            ByteArray::class -> value.binary.data
            Timestamp::class -> value.asTimestamp()
            Float::class -> value.fnum
            Double::class -> value.dnum
            ObjectId::class -> value.asObjectId()
            UUIDWrapper::class -> value.asUUID()
            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
        }
        return result as T
    }

    override fun toString(): String {
        val valueAsString = when (val type = getType()) {
            ValueType.RLM_TYPE_NULL -> "null"
            ValueType.RLM_TYPE_INT -> getLong()
            ValueType.RLM_TYPE_BOOL -> getBoolean()
            ValueType.RLM_TYPE_STRING -> getString()
            ValueType.RLM_TYPE_BINARY -> getByteArray().toString()
            ValueType.RLM_TYPE_TIMESTAMP -> getTimestamp().toString()
            ValueType.RLM_TYPE_FLOAT -> getFloat()
            ValueType.RLM_TYPE_DOUBLE -> getDouble()
            ValueType.RLM_TYPE_OBJECT_ID -> getObjectId().toString()
            ValueType.RLM_TYPE_LINK -> getLink().toString()
            ValueType.RLM_TYPE_UUID -> getUUIDWrapper().toString()
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
        return "RealmValueTransport{type: ${getType()}, value: $valueAsString}"
    }

}

actual typealias RealmQueryArgT = realm_query_arg_t

@JvmInline
actual value class RealmQueryArgsTransport(val value: RealmQueryArgT) {
    actual companion object {
        actual operator fun MemTrackingRealmValueAllocator.invoke(
            queryArgs: Array<RealmValueTransport>
        ): RealmQueryArgsTransport {
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
    }
}
