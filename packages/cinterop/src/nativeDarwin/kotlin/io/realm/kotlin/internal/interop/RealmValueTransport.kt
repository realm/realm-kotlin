package io.realm.kotlin.internal.interop

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import org.mongodb.kbson.ObjectId
import platform.posix.memcpy
import realm_wrapper.realm_query_arg
import realm_wrapper.realm_query_arg_t
import realm_wrapper.realm_value
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

actual typealias RealmValueT = realm_value
actual typealias ValueMemScope = MemScope

// We have no way to convert a CValue to a struct so we need to allocate the struct in native memory
// in both scoped and unscoped places
actual inline fun <R> unscoped(block: (RealmValueT) -> R): R = memScoped { block(alloc()) }
actual inline fun <R> scoped(block: (ValueMemScope) -> R): R = memScoped { block(this) }

actual value class RealmValueTransport actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getLong(): Long = value.integer
    actual inline fun getBoolean(): Boolean = value.boolean
    actual inline fun getString(): String = value.string.toKotlinString()
    actual inline fun getByteArray(): ByteArray = value.asByteArray()
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
            Boolean::class -> value.boolean
            String::class -> value.string.toKotlinString()
            ByteArray::class -> value.asByteArray()
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

    actual companion object {

        private fun createTransport(
            memScope: ValueMemScope,
            type: realm_value_type,
            block: (realm_value_t.() -> Unit)? = null
        ): RealmValueTransport {
            val cValue = memScope.alloc<realm_value_t>()
            cValue.type = ValueType.from(type).nativeValue
            block?.invoke(cValue)
            return RealmValueTransport(cValue)
        }

        actual fun createNull(memScope: ValueMemScope): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_NULL)

        actual operator fun invoke(memScope: ValueMemScope, value: Long): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_INT) { integer = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Boolean): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_BOOL) { boolean = value }

        actual operator fun invoke(memScope: ValueMemScope, value: String): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_STRING) {
                string.set(memScope, value)
            }

        actual operator fun invoke(memScope: ValueMemScope, value: ByteArray): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_BINARY) {
                binary.set(memScope, value)
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Timestamp): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_TIMESTAMP) {
                timestamp.apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Float): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_FLOAT) { fnum = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Double): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_DOUBLE) { dnum = value }

        actual operator fun invoke(memScope: ValueMemScope, value: ObjectId): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_OBJECT_ID) {
                object_id.apply {
                    val objectIdBytes = value.toByteArray()
                    (0 until OBJECT_ID_BYTES_SIZE).map {
                        bytes[it] = objectIdBytes[it].toUByte()
                    }
                }
            }

        actual operator fun invoke(
            memScope: ValueMemScope,
            value: UUIDWrapper
        ): RealmValueTransport = createTransport(memScope, realm_value_type.RLM_TYPE_UUID) {
            uuid.apply {
                value.bytes.usePinned {
                    memcpy(
                        bytes.getPointer(memScope),
                        it.addressOf(0),
                        UUID_BYTES_SIZE.toULong()
                    )
                }
            }
        }

        actual operator fun invoke(memScope: ValueMemScope, value: Link): RealmValueTransport =
            createTransport(memScope, realm_value_type.RLM_TYPE_LINK) {
                link.apply {
                    target_table = value.classKey.key.toUInt()
                    target = value.objKey
                }
            }
    }
}

actual typealias RealmQueryArgT = realm_query_arg

actual value class RealmQueryArgsTransport(val value: RealmQueryArgT) {
    actual companion object {
        actual operator fun invoke(
            scope: ValueMemScope,
            queryArgs: Array<RealmValueTransport>
        ): RealmQueryArgsTransport {
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
    }
}
