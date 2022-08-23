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
) : MutableRealmInt {

    override fun get(): Long {
        obj.checkValid()
        val realmValue = RealmInterop.realm_get_value(obj.objectPointer, propertyKey)
        return converter.realmValueToPublic(realmValue)!!
    }

    override fun set(value: Long) {
        obj.checkValid()
        try {
            val convertedValue = converter.publicToRealmValue(value)
            RealmInterop.realm_set_value(obj.objectPointer, propertyKey, convertedValue, false)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Cannot set `${obj.className}.$${obj.metadata[propertyKey]!!.name}` by `$value`: $NOT_IN_A_TRANSACTION_MSG"
            )
        }
    }

    override fun increment(value: Long) {
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

    override fun decrement(value: Long) = increment(-value)

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other !is MutableRealmInt) return false

        val thisValue = get()
        val otherValue = other.get()
        return thisValue == otherValue
    }

    override fun hashCode(): Int {
        val thisValue = get()
        return thisValue.hashCode()
    }
}

internal class UnmanagedMutableRealmInt(
    private var value: Long = 0
) : MutableRealmInt {

    override fun get(): Long = value

    override fun set(value: Long) {
        this.value = value
    }

    override fun increment(value: Long) {
        this.value = this.value + value
    }

    override fun decrement(value: Long) {
        increment(-value)
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) return true
        if (other == null) return false
        if (other !is MutableRealmInt) return false

        val thisValue = get()
        val otherValue = other.get()
        return thisValue == otherValue
    }

    override fun hashCode(): Int = get().hashCode()
}
