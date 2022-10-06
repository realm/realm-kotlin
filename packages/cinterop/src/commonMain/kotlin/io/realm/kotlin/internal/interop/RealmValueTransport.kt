package io.realm.kotlin.internal.interop

expect class RealmValueT

expect value class RealmValueTransport(val value: RealmValueT) {

    fun getType(): ValueType
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
