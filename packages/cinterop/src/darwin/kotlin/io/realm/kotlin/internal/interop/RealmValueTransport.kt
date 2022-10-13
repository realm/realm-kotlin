package io.realm.kotlin.internal.interop

import kotlinx.cinterop.Arena
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import realm_wrapper.realm_value
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

actual typealias RealmValueT = realm_value
actual typealias TransportMemScope = Arena

actual fun TransportMemScope.clear() = clear()
actual fun createTransportMemScope(): TransportMemScope = Arena()
actual fun TransportMemScope.allocRealmValueT(): RealmValueT = alloc()

actual value class RealmValueTransport actual constructor(
    actual val value: RealmValueT
) {

    actual fun getType(): ValueType = ValueType.from(value.type)

    actual fun getInt(): Int = value.integer.toInt()
    actual fun getShort(): Short = value.integer.toShort()
    actual fun getLong(): Long = value.integer
    actual fun getByte(): Byte = value.integer.toByte()
    actual fun getChar(): Char = value.integer.toInt().toChar()
    actual fun getBoolean(): Boolean = value.boolean
    actual fun getString(): String = value.string.toKotlinString()
    actual fun getByteArray(): ByteArray = value.asByteArray()
    actual fun getTimestamp(): Timestamp = value.asTimestamp()
    actual fun getFloat(): Float = value.fnum
    actual fun getDouble(): Double = value.dnum
    actual fun getObjectIdWrapper(): ObjectIdWrapper = value.asObjectId()
    actual fun getUUIDWrapper(): UUIDWrapper = value.asUUID()

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

        actual fun createNull(): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_NULL)

        actual operator fun invoke(
            memScope: TransportMemScope,
            value: String
        ): RealmValueTransport {
            val cValue = memScope.alloc<realm_value_t>()
            cValue.type = ValueType.from(realm_value_type.RLM_TYPE_STRING).nativeValue
            cValue.string.set(memScope, value)
            return RealmValueTransport(cValue)
        }

        actual operator fun invoke(value: Int): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
                cValue.integer = value.toLong()
            }

        actual operator fun invoke(value: Short): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
                cValue.integer = value.toLong()
            }

        actual operator fun invoke(value: Long): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
                cValue.integer = value
            }

        actual operator fun invoke(value: Byte): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
                cValue.integer = value.toLong()
            }

        actual operator fun invoke(value: Char): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
                cValue.integer = value.code.toLong()
            }

        actual operator fun invoke(value: Boolean): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_BOOL) { _, cValue ->
                cValue.boolean = value
            }

        actual operator fun invoke(value: String): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_STRING) { memScope, cValue ->
                cValue.string.set(memScope, value)
            }

        actual operator fun invoke(value: ByteArray): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_BINARY) { memScope, cValue ->
                cValue.binary.set(memScope, value)
            }

        actual operator fun invoke(value: Timestamp): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_TIMESTAMP) { _, cValue ->
                cValue.timestamp.apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }

        actual operator fun invoke(value: Float): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_FLOAT) { _, cValue ->
                cValue.fnum = value
            }

        actual operator fun invoke(value: Double): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_DOUBLE) { _, cValue ->
                cValue.dnum = value
            }

        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_OBJECT_ID) { _, cValue ->
                cValue.object_id.apply {
                    (0 until OBJECT_ID_BYTES_SIZE).map {
                        bytes[it] = value.bytes[it].toUByte()
                    }
                }
            }

        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport =
            setTypeAndValue(realm_value_type.RLM_TYPE_UUID) { memScope, cValue ->
                cValue.uuid.apply {
                    value.bytes.usePinned {
                        memcpy(
                            bytes.getPointer(memScope),
                            it.addressOf(0),
                            UUID_BYTES_SIZE.toULong()
                        )
                    }
                }
            }

        private fun RealmValueTransport.getReadableValueFromType(type: realm_value_type): String =
            when (type) {
                realm_value_type.RLM_TYPE_NULL -> "null"
                realm_value_type.RLM_TYPE_INT -> this.value.integer.toString()
                realm_value_type.RLM_TYPE_BOOL -> this.value.boolean.toString()
                realm_value_type.RLM_TYPE_STRING -> this.value.string.toKotlinString()
                realm_value_type.RLM_TYPE_BINARY -> this.value.asByteArray().map { it }
                    .joinToString(",")
                realm_value_type.RLM_TYPE_TIMESTAMP -> this.value.asTimestamp().toString()
                realm_value_type.RLM_TYPE_FLOAT -> this.value.fnum.toString()
                realm_value_type.RLM_TYPE_DOUBLE -> this.value.dnum.toString()
//                realm_value_type.RLM_TYPE_DECIMAL128 -> this.value..toString()
                realm_value_type.RLM_TYPE_OBJECT_ID -> this.value.asObjectId().toString()
//                realm_value_type.RLM_TYPE_LINK -> this.value..toString()
                realm_value_type.RLM_TYPE_UUID -> this.value.asUUID().toString()
                else -> throw IllegalArgumentException("Wrong type: $type")
            }

        private fun setTypeAndValue(
            type: realm_value_type,
            block: ((memScope: Arena, cValue: realm_value_t) -> Unit)? = null
        ): RealmValueTransport {
            val memScope = Arena()
            val cValue = memScope.alloc<realm_value_t>()
            cValue.type = ValueType.from(type).nativeValue
            block?.invoke(memScope, cValue)
            val transport = RealmValueTransport(cValue)
            return transport
        }
    }
}

//package io.realm.kotlin.internal.interop
//
//import kotlinx.cinterop.Arena
//import kotlinx.cinterop.addressOf
//import kotlinx.cinterop.alloc
//import kotlinx.cinterop.set
//import kotlinx.cinterop.usePinned
//import platform.posix.memcpy
//import realm_wrapper.realm_value_t
//import realm_wrapper.realm_value_type
//
////actual typealias RealmValueT = realm_value
//
////actual value class RealmValueTransport(val value: RealmValueT) {
//actual class RealmValueTransport(
//    private val memScope: Arena,
//    val value: realm_value_t
//) {
//
//    actual fun free() = memScope.clear()
//
//    actual fun getType(): ValueType = ValueType.from(value.type)
//
//    actual fun getInt(): Int = value.integer.toInt()
//    actual fun getShort(): Short = value.integer.toShort()
//    actual fun getLong(): Long = value.integer
//    actual fun getByte(): Byte = value.integer.toByte()
//    actual fun getChar(): Char = value.integer.toInt().toChar()
//    actual fun getBoolean(): Boolean = value.boolean
//    actual fun getString(): String = value.string.toKotlinString()
//    actual fun getByteArray(): ByteArray = value.asByteArray()
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
//            Boolean::class -> value.boolean
//            String::class -> value.string.toKotlinString()
//            ByteArray::class -> value.asByteArray()
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
//            setTypeAndValue(realm_value_type.RLM_TYPE_NULL)
//
//        actual operator fun invoke(value: Int): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
//                cValue.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Short): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
//                cValue.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Long): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
//                cValue.integer = value
//            }
//
//        actual operator fun invoke(value: Byte): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
//                cValue.integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Char): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) { _, cValue ->
//                cValue.integer = value.code.toLong()
//            }
//
//        actual operator fun invoke(value: Boolean): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_BOOL) { _, cValue ->
//                cValue.boolean = value
//            }
//
//        actual operator fun invoke(value: String): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_STRING) { memScope, cValue ->
//                cValue.string.set(memScope, value)
//            }
//
//        actual operator fun invoke(value: ByteArray): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_BINARY) { memScope, cValue ->
//                cValue.binary.set(memScope, value)
//            }
//
//        actual operator fun invoke(value: Timestamp): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_TIMESTAMP) { memScope, cValue ->
//                cValue.timestamp.apply {
//                    seconds = value.seconds
//                    nanoseconds = value.nanoSeconds
//                }
//            }
//
//        actual operator fun invoke(value: Float): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_FLOAT) { _, cValue ->
//                cValue.fnum = value
//            }
//
//        actual operator fun invoke(value: Double): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_DOUBLE) { _, cValue ->
//                cValue.dnum = value
//            }
//
//        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_OBJECT_ID) { _, cValue ->
//                cValue.object_id.apply {
//                    (0 until OBJECT_ID_BYTES_SIZE).map {
//                        bytes[it] = value.bytes[it].toUByte()
//                    }
//                }
//            }
//
//        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_UUID) { memScope, cValue ->
//                cValue.uuid.apply {
//                    value.bytes.usePinned {
//                        memcpy(
//                            bytes.getPointer(memScope),
//                            it.addressOf(0),
//                            UUID_BYTES_SIZE.toULong()
//                        )
//                    }
//                }
//            }
//
//        private fun RealmValueTransport.getReadableValueFromType(type: realm_value_type): String =
//            when (type) {
//                realm_value_type.RLM_TYPE_NULL -> "null"
//                realm_value_type.RLM_TYPE_INT -> this.value.integer.toString()
//                realm_value_type.RLM_TYPE_BOOL -> this.value.boolean.toString()
//                realm_value_type.RLM_TYPE_STRING -> this.value.string.toKotlinString()
//                realm_value_type.RLM_TYPE_BINARY -> this.value.asByteArray().map { it }
//                    .joinToString(",")
//                realm_value_type.RLM_TYPE_TIMESTAMP -> this.value.asTimestamp().toString()
//                realm_value_type.RLM_TYPE_FLOAT -> this.value.fnum.toString()
//                realm_value_type.RLM_TYPE_DOUBLE -> this.value.dnum.toString()
////                realm_value_type.RLM_TYPE_DECIMAL128 -> this.value..toString()
//                realm_value_type.RLM_TYPE_OBJECT_ID -> this.value.asObjectId().toString()
////                realm_value_type.RLM_TYPE_LINK -> this.value..toString()
//                realm_value_type.RLM_TYPE_UUID -> this.value.asUUID().toString()
//                else -> throw IllegalArgumentException("Wrong type: $type")
//            }
//
//        private fun setTypeAndValue(
//            type: realm_value_type,
//            block: ((memScope: Arena, cValue: realm_value_t) -> Unit)? = null
//        ): RealmValueTransport {
//            println("---> === creating scope")
//            val memScope = Arena()
//            println("---> === instantiating struct")
//            val cValue = memScope.alloc<realm_value_t>()
//            println("---> === setting type: $type")
//            cValue.type = ValueType.from(type).nativeValue
//            println("---> === calling specific constructor")
//            block?.invoke(memScope, cValue)
//            println("---> === instantiating transport object")
//            val transport = RealmValueTransport(memScope, cValue)
//            println("---> === done instantiating transport")
//            return transport
//        }
//    }
//}


//package io.realm.kotlin.internal.interop
//
//import kotlinx.cinterop.addressOf
//import kotlinx.cinterop.alloc
//import kotlinx.cinterop.memScoped
//import kotlinx.cinterop.set
//import kotlinx.cinterop.usePinned
//import platform.posix.memcpy
//import realm_wrapper.realm_value
//import realm_wrapper.realm_value_t
//import realm_wrapper.realm_value_type
//
//actual typealias RealmValueT = realm_value
//
//actual value class RealmValueTransport(val value: RealmValueT) {
//
//    actual fun getType(): ValueType = ValueType.from(value.type)
//
//    actual fun getInt(): Int = value.integer.toInt()
//    actual fun getShort(): Short = value.integer.toShort()
//    actual fun getLong(): Long = value.integer
//    actual fun getByte(): Byte = value.integer.toByte()
//    actual fun getChar(): Char = value.integer.toInt().toChar()
//    actual fun getBoolean(): Boolean = value.boolean
//    actual fun getString(): String = value.string.toKotlinString()
//    actual fun getByteArray(): ByteArray = value.asByteArray()
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
//            Boolean::class -> value.boolean
//            String::class -> value.string.toKotlinString()
//            ByteArray::class -> value.asByteArray()
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
//            setTypeAndValue(realm_value_type.RLM_TYPE_NULL)
//
//        actual operator fun invoke(value: Int): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) {
//                integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Short): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) {
//                integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Long): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) {
//                integer = value
//            }
//
//        actual operator fun invoke(value: Byte): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) {
//                integer = value.toLong()
//            }
//
//        actual operator fun invoke(value: Char): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_INT) {
//                integer = value.code.toLong()
//            }
//
//        actual operator fun invoke(value: Boolean): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_BOOL) {
//                boolean = value
//            }
//
//        actual operator fun invoke(value: String): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_STRING) {
//                memScoped { string.set(memScope, value) }
//            }
//
//        actual operator fun invoke(value: ByteArray): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_BINARY) {
//                memScoped { binary.set(memScope, value) }
//            }
//
//        actual operator fun invoke(value: Timestamp): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_TIMESTAMP) {
//                timestamp.apply {
//                    seconds = value.seconds
//                    nanoseconds = value.nanoSeconds
//                }
//            }
//
//        actual operator fun invoke(value: Float): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_FLOAT) {
//                fnum = value
//            }
//
//        actual operator fun invoke(value: Double): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_DOUBLE) {
//                dnum = value
//            }
//
//        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_OBJECT_ID) {
//                object_id.apply {
//                    (0 until OBJECT_ID_BYTES_SIZE).map {
//                        bytes[it] = value.bytes[it].toUByte()
//                    }
//                }
//            }
//
//        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport =
//            setTypeAndValue(realm_value_type.RLM_TYPE_UUID) {
//                uuid.apply {
//                    value.bytes.usePinned {
//                        memScoped {
//                            memcpy(
//                                bytes.getPointer(memScope),
//                                it.addressOf(0),
//                                UUID_BYTES_SIZE.toULong()
//                            )
//                        }
//                    }
//                }
//            }
//
//        private fun RealmValueTransport.getReadableValueFromType(type: realm_value_type): String =
//            when (type) {
//                realm_value_type.RLM_TYPE_NULL -> "null"
//                realm_value_type.RLM_TYPE_INT -> this.value.integer.toString()
//                realm_value_type.RLM_TYPE_BOOL -> this.value.boolean.toString()
//                realm_value_type.RLM_TYPE_STRING -> this.value.string.toKotlinString()
//                realm_value_type.RLM_TYPE_BINARY -> this.value.asByteArray().map { it }
//                    .joinToString(",")
//                realm_value_type.RLM_TYPE_TIMESTAMP -> this.value.asTimestamp().toString()
//                realm_value_type.RLM_TYPE_FLOAT -> this.value.fnum.toString()
//                realm_value_type.RLM_TYPE_DOUBLE -> this.value.dnum.toString()
////                realm_value_type.RLM_TYPE_DECIMAL128 -> this.value..toString()
//                realm_value_type.RLM_TYPE_OBJECT_ID -> this.value.asObjectId().toString()
////                realm_value_type.RLM_TYPE_LINK -> this.value..toString()
//                realm_value_type.RLM_TYPE_UUID -> this.value.asUUID().toString()
//                else -> throw IllegalArgumentException("Wrong type: $type")
//            }
//
//        private fun setTypeAndValue(
//            type: realm_value_type,
//            block: (realm_value_t.() -> Unit)? = null
//        ): RealmValueTransport {
//            val cValue = memScoped { alloc<realm_value_t>() }
//            cValue.type = ValueType.from(type).nativeValue
//            block?.invoke(cValue)
//            val res = RealmValueTransport(cValue)
//            println("---> constructor type: $type, value: ${res.getReadableValueFromType(type)}")
//            return res
//
//            // goes out of scope already before leaving this function
////            val res: RealmValueTransport = memScoped {
////                val cValue: realm_value_t = alloc<realm_value_t>()
////                    .apply {
////                        this.type = type
////                        block.invoke(this)
////                    }
////                RealmValueTransport(cValue)
////            }
////            // value is already out of scope
////            println("---> constructor type: $type, value: ${res.getReadableValueFromType(type)}")
////            return res
//
//            // goes out of scope already before leaving this function
////            val cValue = memScoped {
////                alloc<realm_value_t>()
////                    .apply {
////                        this.type = type
////                        block.invoke(this)
////                    }
////            }
////            val res = RealmValueTransport(cValue)
////            // value is already out of scope
////            println("---> constructor type: $type, value: ${res.getReadableValueFromType(type)}")
////            return res
//
////            return memScoped { alloc<realm_value_t>() }
////                .apply {
////                    this.type = ValueType.from(type).nativeValue
////                    block.invoke(this)
////                }.let {
////                    val res = RealmValueTransport(it)
////                    println("---> constructor type: $type, value: ${res.getReadableValueFromType(type)}")
////                    res
////                }
//        }
//    }
//}
