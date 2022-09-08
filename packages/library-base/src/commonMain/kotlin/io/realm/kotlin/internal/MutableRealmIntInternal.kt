package io.realm.kotlin.internal

import io.realm.kotlin.internal.RealmObjectHelper.NOT_IN_A_TRANSACTION_MSG
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.MutableRealmInt

internal class ManagedMutableRealmInt(
    private val obj: RealmObjectReference<out BaseRealmObject>,
    private val propertyKey: PropertyKey,
    private val converter: RealmValueConverter<Long>
) : MutableRealmInt() {

    override fun get(): Long {
        obj.checkValid()
        val realmValue = RealmInterop.realm_get_value(obj.objectPointer, propertyKey)
        return converter.realmValueToPublic(realmValue)!!
    }

    override fun set(value: Number) {
        obj.checkValid()
        try {
            val convertedValue = converter.publicToRealmValue(value.toLong())
            RealmInterop.realm_set_value(obj.objectPointer, propertyKey, convertedValue, false)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Cannot set `${obj.className}.$${obj.metadata[propertyKey]!!.name}` by `$value`: $NOT_IN_A_TRANSACTION_MSG"
            )
        }
    }

    override fun increment(value: Number) {
        additionInternal(value.toLong())
    }

    override fun decrement(value: Number) = increment(-value.toLong())

    private fun additionInternal(value: Long) {
        obj.checkValid()
        try {
            RealmInterop.realm_object_add_int(obj.objectPointer, propertyKey, value)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Cannot increment/decrement `${obj.className}.$${obj.metadata[propertyKey]!!.name}` by `$value`: $NOT_IN_A_TRANSACTION_MSG",
            )
        }
    }
}

internal class UnmanagedMutableRealmInt(
    private var value: Long = 0
) : MutableRealmInt() {

    override fun get(): Long = value

    override fun set(value: Number) {
        this.value = value.toLong()
    }

    override fun increment(value: Number) {
        this.value = this.value + value.toLong()
    }

    override fun decrement(value: Number) = increment(-value.toLong())

    // override fun plusAssign(other: Number) {
    //     value += other.toLong()
    // }
    //
    // override fun minusAssign(other: Number) {
    //     value -= other.toLong()
    // }

    // override fun equals(other: Any?): Boolean {
    //     if (other === this) return true
    //     if (other == null) return false
    //     if (other !is MutableRealmInt) return false
    //
    //     val thisValue = get()
    //     val otherValue = other.get()
    //     return thisValue == otherValue
    // }

    // override fun hashCode(): Int = get().hashCode()
}
