package io.realm.kotlin.internal.interop

expect class RealmValueT
expect class ValueMemScope

expect fun createTransportMemScope(): ValueMemScope
expect fun ValueMemScope.allocRealmValueT(): RealmValueT
expect fun <R> valueMemScope(freeJvmScope: Boolean = true, block: ValueMemScope.() -> R): R

expect value class RealmValueTransport(val value: RealmValueT) {

    inline fun getType(): ValueType

    inline fun getInt(): Int
    inline fun getShort(): Short
    inline fun getLong(): Long
    inline fun getByte(): Byte
    inline fun getChar(): Char
    inline fun getBoolean(): Boolean
    inline fun getString(): String
    inline fun getByteArray(): ByteArray
    inline fun getTimestamp(): Timestamp
    inline fun getFloat(): Float
    inline fun getDouble(): Double
    inline fun getObjectIdWrapper(): ObjectIdWrapper
    inline fun getUUIDWrapper(): UUIDWrapper
    inline fun getLink(): Link

    inline fun <reified T> get(): T

    companion object {
        fun createNull(memScope: ValueMemScope): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Int): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Short): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Long): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Byte): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Char): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Boolean): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: String): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: ByteArray): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Timestamp): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Float): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Double): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: ObjectIdWrapper): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: UUIDWrapper): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Link): RealmValueTransport
    }
}
