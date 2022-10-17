package io.realm.kotlin.internal.interop

actual typealias RealmValueT = realm_value_t
actual typealias ValueMemScope = MemScope

actual fun createTransportMemScope(): ValueMemScope = MemScope()
actual fun ValueMemScope.clearValueToStruct() = Unit // Do nothing, Swig will call 'delete'
actual fun ValueMemScope.allocRealmValueT(): RealmValueT = realm_value_t()
actual fun <R> valueMemScope(freeScope: Boolean, block: ValueMemScope.() -> R): R {
    val scope = MemScope()
    try {
        return block(scope)
    } finally {
        if (freeScope) scope.free()
    }
}

@JvmInline
actual value class RealmValueTransport actual constructor(
    actual val value: RealmValueT
) {

    actual inline fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun getInt(): Int = value.integer.toInt()
    actual inline fun getShort(): Short = value.integer.toShort()
    actual inline fun getLong(): Long = value.integer
    actual inline fun getByte(): Byte = value.integer.toByte()
    actual inline fun getChar(): Char = value.integer.toInt().toChar()
    actual inline fun getBoolean(): Boolean = value._boolean
    actual inline fun getString(): String = value.string
    actual inline fun getByteArray(): ByteArray = value.binary.data
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
            Boolean::class -> value._boolean
            String::class -> value.string
            ByteArray::class -> value.binary.data
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
            memScope: ValueMemScope,
            type: Int,
            block: (RealmValueT.() -> Unit)? = null
        ): RealmValueTransport {
            val cValue = realm_value_t()
            cValue.type = type
            block?.invoke(cValue)
            memScope.manageRealmValue(cValue)
            return RealmValueTransport(cValue)
        }

        actual fun createNull(memScope: ValueMemScope): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_NULL)

        actual operator fun invoke(memScope: ValueMemScope, value: Int): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Short): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Long): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_INT) { integer = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Byte): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_INT) { integer = value.toLong() }

        actual operator fun invoke(memScope: ValueMemScope, value: Char): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_INT) {
                integer = value.code.toLong()
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Boolean): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_BOOL) { _boolean = value }

        actual operator fun invoke(memScope: ValueMemScope, value: String): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_STRING) { string = value }

        actual operator fun invoke(memScope: ValueMemScope, value: ByteArray): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_BINARY) {
                binary = realm_binary_t().apply {
                    data = value
                    size = value.size.toLong()
                }
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Timestamp): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_TIMESTAMP) {
                timestamp = realm_timestamp_t().apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }

        actual operator fun invoke(memScope: ValueMemScope, value: Float): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_FLOAT) { fnum = value }

        actual operator fun invoke(memScope: ValueMemScope, value: Double): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_DOUBLE) { dnum = value }

        actual operator fun invoke(
            memScope: ValueMemScope,
            value: ObjectIdWrapper
        ): RealmValueTransport = createTransport(memScope, realm_value_type_e.RLM_TYPE_OBJECT_ID) {
            object_id = realm_object_id_t().apply {
                val data = ShortArray(OBJECT_ID_BYTES_SIZE)
                (0 until OBJECT_ID_BYTES_SIZE).map { i ->
                    data[i] = value.bytes[i].toShort()
                }
                bytes = data
            }
        }

        actual operator fun invoke(
            memScope: ValueMemScope,
            value: UUIDWrapper
        ): RealmValueTransport = createTransport(memScope, realm_value_type_e.RLM_TYPE_UUID) {
            uuid = realm_uuid_t().apply {
                val data = ShortArray(UUID_BYTES_SIZE)
                (0 until UUID_BYTES_SIZE).map { index ->
                    data[index] = value.bytes[index].toShort()
                }
                bytes = data
            }
        }

        actual operator fun invoke(memScope: ValueMemScope, value: Link): RealmValueTransport =
            createTransport(memScope, realm_value_type_e.RLM_TYPE_LINK) {
                this.link = realm_link_t().apply {
                    target_table = value.classKey.key
                    target = value.objKey
                }
            }
    }
}
