package io.realm.internal

import io.realm.RealmInstant
import io.realm.internal.interop.Timestamp

internal data class RealmInstantImpl(override val seconds: Long, override val nanoSeconds: Int) : Timestamp, RealmInstant {
    constructor(ts: Timestamp) : this(ts.seconds, ts.nanoSeconds)

    override val epochSeconds: Long
        get() = seconds

    override val nanosecondsOfSecond: Int
        get() = nanoSeconds

    override fun compareTo(other: RealmInstant): Int {
        return when {
            this.epochSeconds < other.epochSeconds -> -1
            this.epochSeconds > other.epochSeconds -> 1
            else -> this.nanosecondsOfSecond.compareTo(other.nanosecondsOfSecond)
        }
    }

    override fun toString(): String {
        return "RealmInstant(epochSeconds=$epochSeconds, nanosecondsOfSecond=$nanosecondsOfSecond)"
    }
}
