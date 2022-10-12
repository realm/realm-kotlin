package io.realm.kotlin.internal.interop

actual typealias TransportMemScope = MemScope
actual fun TransportMemScope.clear() = this.free()
actual typealias RealmValueT = realm_value_t

@JvmInline
actual value class RealmValueTransport actual constructor(
    actual val value: Pair<TransportMemScope, RealmValueT>
) {

    actual fun memScope(): TransportMemScope = value.first
    actual fun free() = value.first.free()

    actual fun getType(): ValueType = ValueType.from(value.second.type)

    actual fun getInt(): Int = value.second.integer.toInt()
    actual fun getShort(): Short = value.second.integer.toShort()
    actual fun getLong(): Long = value.second.integer
    actual fun getByte(): Byte = value.second.integer.toByte()
    actual fun getChar(): Char = value.second.integer.toInt().toChar()
    actual fun getBoolean(): Boolean = value.second._boolean
    actual fun getString(): String = value.second.string
    actual fun getByteArray(): ByteArray = value.second.binary.data
    actual fun getTimestamp(): Timestamp = value.second.asTimestamp()
    actual fun getFloat(): Float = value.second.fnum
    actual fun getDouble(): Double = value.second.dnum
    actual fun getObjectIdWrapper(): ObjectIdWrapper = value.second.asObjectId()
    actual fun getUUIDWrapper(): UUIDWrapper = value.second.asUUID()

    actual inline fun <reified T> get(): T {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val result = when (T::class) {
            Int::class -> value.second.integer.toInt()
            Short::class -> value.second.integer.toShort()
            Long::class -> value.second.integer
            Byte::class -> value.second.integer.toByte()
            Char::class -> value.second.integer.toInt().toChar()
            Boolean::class -> value.second._boolean
            String::class -> value.second.string
            ByteArray::class -> value.second.binary.data
            Timestamp::class -> value.second.asTimestamp()
            Float::class -> value.second.fnum
            Double::class -> value.second.dnum
            ObjectIdWrapper::class -> value.second.asObjectId()
            UUIDWrapper::class -> value.second.asUUID()
            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
        }
        return result as T
    }

    actual companion object {

        actual fun createNull(): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_NULL) { /* noop */ }

        actual operator fun invoke(value: Int): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_INT) {
                it.integer = value.toLong()
            }

        actual operator fun invoke(value: Short): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_INT) {
                it.integer = value.toLong()
            }

        actual operator fun invoke(value: Long): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_INT) {
                it.integer = value
            }

        actual operator fun invoke(value: Byte): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_INT) {
                it.integer = value.toLong()
            }

        actual operator fun invoke(value: Char): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_INT) {
                it.integer = value.code.toLong()
            }

        actual operator fun invoke(value: Boolean): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_BOOL) {
                it._boolean = value
            }

        actual operator fun invoke(value: String): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_STRING) {
                it.string = value
            }

        actual operator fun invoke(value: ByteArray): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_BINARY) {
                it.binary = realm_binary_t().apply {
                    data = value
                    size = value.size.toLong()
                }
            }

        actual operator fun invoke(value: Timestamp): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_TIMESTAMP) {
                it.timestamp = realm_timestamp_t().apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }

        actual operator fun invoke(value: Float): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_FLOAT) {
                it.fnum = value
            }

        actual operator fun invoke(value: Double): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_DOUBLE) {
                it.dnum = value
            }

        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_OBJECT_ID) {
                it.object_id = realm_object_id_t().apply {
                    val data = ShortArray(OBJECT_ID_BYTES_SIZE)
                    (0 until OBJECT_ID_BYTES_SIZE).map { i ->
                        data[i] = value.bytes[i].toShort()
                    }
                    bytes = data
                }
            }

        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport =
            createTransport(realm_value_type_e.RLM_TYPE_UUID) {
                it.uuid = realm_uuid_t().apply {
                    val data = ShortArray(UUID_BYTES_SIZE)
                    (0 until UUID_BYTES_SIZE).map { index ->
                        data[index] = value.bytes[index].toShort()
                    }
                    bytes = data
                }
            }

        private fun createTransport(
            type: Int,
            block: ((realm_value_t) -> Unit)? = null
        ): RealmValueTransport {
            return realm_value_t()
                .apply {
                    this.type = type
                    block?.invoke(this)
                }.let {
                    RealmValueTransport(Pair(MemScope(), it))
                }
        }
    }
}

//package io.realm.kotlin.internal.interop
//
//actual typealias RealmValueT = realm_value_t
//
//@JvmInline
//actual value class RealmValueTransport(val value: RealmValueT) {
//
//    actual fun getType(): ValueType = ValueType.from(value.type)
//
//    actual fun getInt(): Int = value.integer.toInt()
//    actual fun getShort(): Short = value.integer.toShort()
//    actual fun getLong(): Long = value.integer
//    actual fun getByte(): Byte = value.integer.toByte()
//    actual fun getChar(): Char = value.integer.toInt().toChar()
//    actual fun getBoolean(): Boolean = value._boolean
//    actual fun getString(): String = value.string
//    actual fun getByteArray(): ByteArray = value.binary.data
//    actual fun getTimestamp(): Timestamp = value.asTimestamp()
//    actual fun getFloat(): Float = value.fnum
//    actual fun getDouble(): Double = value.dnum
//    actual fun getObjectIdWrapper(): ObjectIdWrapper = value.asObjectId()
//    actual fun getUUIDWrapper(): UUIDWrapper = value.asUUID()
//
//    actual inline fun <reified T> get(): T {
//        @Suppress("IMPLICIT_CAST_TO_ANY")
//        val result = when (T::class) {
//            Int::class -> value.integer.toInt()
//            Short::class -> value.integer.toShort()
//            Long::class -> value.integer
//            Byte::class -> value.integer.toByte()
//            Char::class -> value.integer.toInt().toChar()
//            Boolean::class -> value._boolean
//            String::class -> value.string
//            ByteArray::class -> value.binary.data
//            Timestamp::class -> value.asTimestamp()
//            Float::class -> value.fnum
//            Double::class -> value.dnum
//            ObjectIdWrapper::class -> value.asObjectId()
//            UUIDWrapper::class -> value.asUUID()
//            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
//        }
//        return result as T
//    }
//
//    actual companion object {
//
//        actual fun createNull(): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_NULL) { /* noop */ }
//
//        actual operator fun invoke(value: Int): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_INT) {
//                it.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Short): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_INT) {
//                it.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Long): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_INT) {
//                it.integer = value
//            }
//
//        actual operator fun invoke(value: Byte): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_INT) {
//                it.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Char): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_INT) {
//                it.integer = value.code.toLong()
//            }
//
//        actual operator fun invoke(value: Boolean): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_BOOL) {
//                it._boolean = value
//            }
//
//        actual operator fun invoke(value: String): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_STRING) {
//                it.string = value
//            }
//
//        actual operator fun invoke(value: ByteArray): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_BINARY) {
//                it.binary = realm_binary_t().apply {
//                    data = value
//                    size = value.size.toLong()
//                }
//            }
//
//        actual operator fun invoke(value: Timestamp): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_TIMESTAMP) {
//                it.timestamp = realm_timestamp_t().apply {
//                    seconds = value.seconds
//                    nanoseconds = value.nanoSeconds
//                }
//            }
//
//        actual operator fun invoke(value: Float): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_FLOAT) {
//                it.fnum = value
//            }
//
//        actual operator fun invoke(value: Double): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_DOUBLE) {
//                it.dnum = value
//            }
//
//        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_OBJECT_ID) {
//                it.object_id = realm_object_id_t().apply {
//                    val data = ShortArray(OBJECT_ID_BYTES_SIZE)
//                    (0 until OBJECT_ID_BYTES_SIZE).map { i ->
//                        data[i] = value.bytes[i].toShort()
//                    }
//                    bytes = data
//                }
//            }
//
//        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport =
//            createTransport(realm_value_type_e.RLM_TYPE_UUID) {
//                it.uuid = realm_uuid_t().apply {
//                    val data = ShortArray(UUID_BYTES_SIZE)
//                    (0 until UUID_BYTES_SIZE).map { index ->
//                        data[index] = value.bytes[index].toShort()
//                    }
//                    bytes = data
//                }
//            }
//
//        private fun createTransport(
//            type: Int,
//            block: ((realm_value_t) -> Unit)? = null
//        ): RealmValueTransport = realm_value_t()
//            .apply {
//                this.type = type
//                block?.invoke(this)
//            }.let {
//                RealmValueTransport(it)
//            }
//    }
//}
