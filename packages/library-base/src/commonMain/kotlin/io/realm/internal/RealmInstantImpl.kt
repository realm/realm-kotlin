package io.realm.internal

import io.realm.RealmInstant
import io.realm.internal.interop.Timestamp

class RealmInstantImpl(seconds: Long, nanoSeconds: Int) : Timestamp(seconds, nanoSeconds), RealmInstant {
    constructor(ts: Timestamp) : this(ts.seconds, ts.nanoSeconds)

    override val epochSeconds: Long
        get() = seconds

    override val nanosecondsOfSecond: Int
        get() = nanoSeconds

    override fun compareTo(other: RealmInstant): Int {
        if (this.epochSeconds < other.epochSeconds) {
            return -1
        } else if (this.epochSeconds > other.epochSeconds) {
            return 1
        } else {
            return this.nanosecondsOfSecond.compareTo(other.nanosecondsOfSecond)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as RealmInstantImpl

        if (epochSeconds != other.epochSeconds) return false
        if (nanosecondsOfSecond != other.nanosecondsOfSecond) return false

        return true
    }

    override fun hashCode(): Int {
        var result = epochSeconds.hashCode()
        result = 31 * result + nanosecondsOfSecond
        return result
    }

    override fun toString(): String {
        return "RealmInstant(epochSeconds=$epochSeconds, nanosecondsOfSecond=$nanosecondsOfSecond)"
    }
}
