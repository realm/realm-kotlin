package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.types.RealmInstant

// Public as constructor is inlined in accessor converter method (Converters.kt)
public data class RealmInstantImpl(override val seconds: Long, override val nanoSeconds: Int) :
    Timestamp, RealmInstant {
    public constructor(ts: Timestamp) : this(ts.seconds, ts.nanoSeconds)

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

private const val MILLI_AS_NANOSECOND: Int = 1_000_000
private const val SEC_AS_MILLISECOND: Int = 1_000

internal fun RealmInstant.toMillis(): Long {
    return epochSeconds * SEC_AS_MILLISECOND + nanosecondsOfSecond / MILLI_AS_NANOSECOND
}

internal fun Long.toRealmInstant(): RealmInstant {
    val seconds = this / SEC_AS_MILLISECOND
    val nanoseconds = this % SEC_AS_MILLISECOND * MILLI_AS_NANOSECOND
    return RealmInstant.from(seconds, nanoseconds.toInt())
}
