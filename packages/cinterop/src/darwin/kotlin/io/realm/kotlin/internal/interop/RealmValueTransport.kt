package io.realm.kotlin.internal.interop

import kotlinx.cinterop.Arena
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import realm_wrapper.realm_value
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

actual typealias RealmValueT = realm_value
actual typealias ValueMemScope = Arena

actual fun createTransportMemScope(): ValueMemScope = Arena()
actual fun ValueMemScope.clearValueToStruct() = clear() // Clear scope in both cases
actual fun ValueMemScope.allocRealmValueT(): RealmValueT = alloc() // alloc adds struct to scope
actual fun <R> valueMemScope(freeScope: Boolean, block: ValueMemScope.() -> R): R {
    val memScope = Arena()
    try {
        return memScope.block()
    } finally {
        // ignore freeScope since we should always free allocated resources for Native
        memScope.clear()
    }
}

actual value class RealmValueTransport actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getInt(): Int = value.integer.toInt()
    actual inline fun getShort(): Short = value.integer.toShort()
    actual inline fun getLong(): Long = value.integer
    actual inline fun getByte(): Byte = value.integer.toByte()
    actual inline fun getChar(): Char = value.integer.toInt().toChar()
    actual inline fun getBoolean(): Boolean = value.boolean
    actual inline fun getString(): String = value.string.toKotlinString()
    actual inline fun getByteArray(): ByteArray = value.asByteArray()
    actual inline fun getTimestamp(): Timestamp = value.asTimestamp()
    actual inline fun getFloat(): Float = value.fnum
    actual inline fun getDouble(): Double = value.dnum
    actual inline fun getObjectIdWrapper(): ObjectIdWrapper = value.asObjectId()
    actual inline fun getUUIDWrapper(): UUIDWrapper = value.asUUID()
    actual inline fun getLink(): Link = value.asLink()

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
            ObjectIdWrapper::class -> value.asObjectId()
            UUIDWrapper::class -> value.asUUID()
            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
        }
        return result as T
    }

    actual companion object {

        private fun createTransport(
            type: realm_value_type,
            block: (realm_value_t.() -> Unit)? = null
        ): RealmValueTransport {
            val memScope = Arena()
            val cValue = memScope.alloc<realm_value_t>()
            cValue.type = ValueType.from(type).nativeValue
            block?.invoke(cValue)
            return RealmValueTransport(cValue)
        }

        actual fun createNull(memScope: ValueMemScope): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_NULL)

        actual operator fun invoke(memScope: ValueMemScope, value: Int): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Short): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Long): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_INT) { integer = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Byte): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Char): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_INT) { integer = value.code.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Boolean): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_BOOL) { boolean = value }

        actual operator fun invoke(memScope: ValueMemScope, value: String): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_STRING) { string.set(memScope, value) }

        actual operator fun invoke(memScope: ValueMemScope, value: ByteArray): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_BINARY) { binary.set(memScope, value) }

        actual operator fun invoke(memScope: ValueMemScope, value: Timestamp): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_TIMESTAMP) {
                timestamp.apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Float): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_FLOAT) { fnum = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Double): RealmValueTransport =
            createTransport(realm_value_type.RLM_TYPE_DOUBLE) { dnum = value }

        actual operator fun invoke(
            memScope: ValueMemScope,
            value: ObjectIdWrapper
        ): RealmValueTransport = createTransport(realm_value_type.RLM_TYPE_OBJECT_ID) {
            object_id.apply {
                (0 until OBJECT_ID_BYTES_SIZE).map {
                    bytes[it] = value.bytes[it].toUByte()
                }
            }
        }

        actual operator fun invoke(
            memScope: ValueMemScope,
            value: UUIDWrapper
        ): RealmValueTransport = createTransport(realm_value_type.RLM_TYPE_UUID) {
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
            createTransport(realm_value_type.RLM_TYPE_LINK) {
                link.apply {
                    target_table = value.classKey.key.toUInt()
                    target = value.objKey
                }
            }
    }
}
