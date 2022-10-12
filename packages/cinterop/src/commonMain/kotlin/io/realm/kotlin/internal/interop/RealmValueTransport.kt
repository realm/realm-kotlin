package io.realm.kotlin.internal.interop

//expect class RealmValueT

//expect value class RealmValueTransport(val value: RealmValueT) {
expect class RealmValueTransport {

    fun free()

    fun getType(): ValueType

    fun getInt(): Int
    fun getShort(): Short
    fun getLong(): Long
    fun getByte(): Byte
    fun getChar(): Char
    fun getBoolean(): Boolean
    fun getString(): String
    fun getByteArray(): ByteArray
    fun getTimestamp(): Timestamp
    fun getFloat(): Float
    fun getDouble(): Double
    fun getObjectIdWrapper(): ObjectIdWrapper
    fun getUUIDWrapper(): UUIDWrapper

    inline fun <reified T> get(): T

    companion object {
        fun createNull(): RealmValueTransport
        operator fun invoke(value: Int): RealmValueTransport
        operator fun invoke(value: Short): RealmValueTransport
        operator fun invoke(value: Long): RealmValueTransport
        operator fun invoke(value: Byte): RealmValueTransport
        operator fun invoke(value: Char): RealmValueTransport
        operator fun invoke(value: Boolean): RealmValueTransport
        operator fun invoke(value: String): RealmValueTransport
        operator fun invoke(value: ByteArray): RealmValueTransport
        operator fun invoke(value: Timestamp): RealmValueTransport
        operator fun invoke(value: Float): RealmValueTransport
        operator fun invoke(value: Double): RealmValueTransport
        operator fun invoke(value: ObjectIdWrapper): RealmValueTransport
        operator fun invoke(value: UUIDWrapper): RealmValueTransport
    }
}


//package io.realm.kotlin.internal.interop
//
//expect class RealmValueT
//
//expect value class RealmValueTransport(val value: RealmValueT) {
//
//    fun getType(): ValueType
//    inline fun <reified T> get(): T
//
//    companion object {
//        fun createNull(): RealmValueTransport
//        operator fun invoke(value: Int): RealmValueTransport
//        operator fun invoke(value: Short): RealmValueTransport
//        operator fun invoke(value: Long): RealmValueTransport
//        operator fun invoke(value: Byte): RealmValueTransport
//        operator fun invoke(value: Char): RealmValueTransport
//        operator fun invoke(value: Boolean): RealmValueTransport
//        operator fun invoke(value: String): RealmValueTransport
//        operator fun invoke(value: ByteArray): RealmValueTransport
//        operator fun invoke(value: Timestamp): RealmValueTransport
//        operator fun invoke(value: Float): RealmValueTransport
//        operator fun invoke(value: Double): RealmValueTransport
//        operator fun invoke(value: ObjectIdWrapper): RealmValueTransport
//        operator fun invoke(value: UUIDWrapper): RealmValueTransport
//    }
//}
