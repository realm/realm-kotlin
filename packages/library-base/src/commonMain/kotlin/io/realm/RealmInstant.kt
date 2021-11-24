package io.realm

import io.realm.internal.interop.Timestamp

/**
 * TODO: Should type be Interface, Normal class or Data Class?
 * TODO: Should name be RealmInstant, RealmDate or something else?
 */
class RealmInstant internal constructor(internal val data: Timestamp) {

    public constructor(epochSeconds: Long, nanoAdjustment: Int) : this(Timestamp(epochSeconds, nanoAdjustment))

    val epochSeconds: Long
        get() = data.epochSeconds

    val nanoAdjustment: Int
        get() = data.nanoAdjustment

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RealmInstant

        if (data != other.data) return false

        return true
    }

    override fun hashCode(): Int {
        return data.hashCode()
    }

    override fun toString(): String {
        return "RealmInstant(epochSeconds=$epochSeconds, nanoAdjustment=$nanoAdjustment)"
    }
}
