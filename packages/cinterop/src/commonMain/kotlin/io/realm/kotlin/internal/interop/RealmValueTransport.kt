package io.realm.kotlin.internal.interop

import org.mongodb.kbson.ObjectId

expect class RealmValueT
expect class ValueMemScope

expect inline fun <R> unscoped(block: (unscopedStruct: RealmValueT) -> R): R
expect inline fun <R> scoped(block: (ValueMemScope) -> R): R

expect value class RealmValueTransport(val value: RealmValueT) {

    inline fun getType(): ValueType

    inline fun getLong(): Long
    inline fun getBoolean(): Boolean
    inline fun getString(): String
    inline fun getByteArray(): ByteArray
    inline fun getTimestamp(): Timestamp
    inline fun getFloat(): Float
    inline fun getDouble(): Double
    inline fun getObjectId(): ObjectId
    inline fun getUUIDWrapper(): UUIDWrapper
    inline fun getLink(): Link

    inline fun <reified T> get(): T

    companion object {
        fun createNull(memScope: ValueMemScope): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Long): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Boolean): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: String): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: ByteArray): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Timestamp): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Float): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Double): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: ObjectId): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: UUIDWrapper): RealmValueTransport
        operator fun invoke(memScope: ValueMemScope, value: Link): RealmValueTransport
    }
}

expect class RealmQueryArgT

expect value class RealmQueryArgsTransport(val value: RealmQueryArgT) {
    companion object {
        operator fun invoke(
            scope: ValueMemScope,
            queryArgs: Array<RealmValueTransport>
        ): RealmQueryArgsTransport
    }
}
