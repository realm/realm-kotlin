package io.realm.kotlin.internal.interop

import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import platform.posix.memcpy
import realm_wrapper.realm_value
import realm_wrapper.realm_value_t
import realm_wrapper.realm_value_type

actual typealias RealmValueT = realm_value

actual value class RealmValueTransport(val value: RealmValueT) {

    actual fun getType(): ValueType = ValueType.from(value.type)

    actual inline fun <reified T> get(): T {
        @Suppress("IMPLICIT_CAST_TO_ANY")
        val result = when (T::class) {
            Int::class -> {
                println("------> get integer 1: ${value.type}")
                println("------> get integer 2: ${value.integer}")
                value.integer.toInt()
            }
            Short::class -> value.integer.toShort()
            Long::class -> value.integer
            Byte::class -> value.integer.toByte()
            Char::class -> value.integer.toInt().toChar()
            Boolean::class -> value.boolean
            String::class -> value.string
            ByteArray::class -> value.binary.data
            Timestamp::class -> value.asTimestamp()
            Float::class -> value.fnum
            Double::class -> value.dnum
            ObjectIdWrapper::class -> value.asObjectId()
            UUIDWrapper::class -> value.asUUID()
            else -> throw IllegalArgumentException("Unsupported type parameter for transport: ${T::class.simpleName}")
        }
        val resultAsT = result as T
        return resultAsT
    }

    actual companion object {

        actual fun createNull(): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_NULL) { _, _ -> /* noop */ }
            println("------> type: ${transport.getType()}")
            return transport
        }

        actual operator fun invoke(value: Int): RealmValueTransport {
            return memScoped {
                val cValue = alloc<realm_value_t>()
                cValue.type = realm_value_type.RLM_TYPE_INT
                cValue.integer = value.toLong()
                println("------> 1 Int type: ${cValue.type}")
                println("------> 1 Int value: ${cValue.integer}")
                val transport = RealmValueTransport(cValue)
                println("------> 2 Int type: ${transport.value.type}")
                println("------> 2 Int value: ${transport.value.integer}")
                transport
            }
//            val transport = createTransport(realm_value_type.RLM_TYPE_INT) { cValue, _ ->
//                cValue.integer = value.toLong()
//            }
//            println("------> type: ${transport.value.type}, value: ${transport.value.integer}")
//            return transport
        }

        actual operator fun invoke(value: Short): RealmValueTransport {
            memScoped {
                val cValue = alloc<realm_value_t>()
                cValue.type = realm_value_type.RLM_TYPE_INT
                cValue.integer = value.toLong()
                println("------> 1 Short type: ${cValue.type}")
                println("------> 1 Short value: ${cValue.integer}")
                val transport = RealmValueTransport(cValue)
                println("------> 2 Short type: ${transport.value.type}")
                println("------> 2 Short value: ${transport.value.integer}")
                return transport
            }
//            val transport = createTransport(realm_value_type.RLM_TYPE_INT) { cValue, _ ->
//                cValue.integer = value.toLong()
//            }
//            println("------> type: ${transport.value.type}, value: ${transport.value.integer}")
//            return transport
        }

        actual operator fun invoke(value: Long): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_INT) { cValue, _ ->
                cValue.integer = value
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.integer}")
            return transport
        }

        actual operator fun invoke(value: Byte): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_INT) { cValue, _ ->
                cValue.integer = value.toLong()
            }
            println("------> type: ${transport.value.type}")
            println("------> value: ${transport.value.integer}")
//            println("------> type: ${transport.value.type}, value: ${transport.value.integer}")
            return transport
        }

        actual operator fun invoke(value: Char): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_INT) { cValue, _ ->
                cValue.integer = value.code.toLong()
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.integer}")
            return transport
        }

        actual operator fun invoke(value: Boolean): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_BOOL) { cValue, _ ->
                cValue.boolean = value
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.boolean}")
            return transport
        }

        actual operator fun invoke(value: String): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_STRING) { cValue, memScope ->
                cValue.string.set(memScope, value)
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.string.toKotlinString()}")
            return transport
        }

        actual operator fun invoke(value: ByteArray): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_BINARY) { cValue, memScope ->
                cValue.binary.set(memScope, value)
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.asByteArray()}")
            return transport
        }

        actual operator fun invoke(value: Timestamp): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_TIMESTAMP) { cValue, _ ->
                cValue.timestamp.apply {
                    seconds = value.seconds
                    nanoseconds = value.nanoSeconds
                }
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.asTimestamp()}")
            return transport
        }

        actual operator fun invoke(value: Float): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_FLOAT) { cValue, _ ->
                cValue.fnum = value
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.fnum}")
            return transport
        }

        actual operator fun invoke(value: Double): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_DOUBLE) { cValue, _ ->
                cValue.dnum = value
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.dnum}")
            return transport
        }

        actual operator fun invoke(value: ObjectIdWrapper): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_OBJECT_ID) { cValue, _ ->
                cValue.object_id.apply {
                    (0 until OBJECT_ID_BYTES_SIZE).map {
                        bytes[it] = value.bytes[it].toUByte()
                    }
                }
            }
            println("------> type: ${transport.value.type}, value: ${transport.value.asObjectId()}")
            return transport
        }

        actual operator fun invoke(value: UUIDWrapper): RealmValueTransport {
            val transport = createTransport(realm_value_type.RLM_TYPE_UUID) { cValue, memScope ->
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
            println("------> type: ${transport.value.type}, value: ${transport.value.asUUID()}")
            return transport
        }

        private fun createTransport(
            type: realm_value_type,
            block: (realm_value_t, MemScope) -> Unit
        ): RealmValueTransport {
            memScoped {
                return alloc<realm_value_t>()
                    .apply {
                        this.type = type
                        block.invoke(this, this@memScoped)
                    }.let {
                        RealmValueTransport(it)
                    }
            }
        }
    }
}
