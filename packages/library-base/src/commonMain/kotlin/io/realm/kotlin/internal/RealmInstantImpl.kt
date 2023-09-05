package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.Timestamp
import io.realm.kotlin.types.RealmInstant
import org.mongodb.kbson.BsonDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

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

public fun RealmInstant.toDuration(): Duration {
    return epochSeconds.seconds + nanosecondsOfSecond.nanoseconds
}

internal fun Duration.toRealmInstant(): RealmInstant {
    val seconds: Long = this.inWholeSeconds
    val nanos: Duration = (this - seconds.seconds)
    return RealmInstant.from(seconds, nanos.inWholeNanoseconds.toInt())
}

internal fun RealmInstant.restrictToMillisPrecision() =
    toDuration().inWholeMilliseconds.milliseconds.toRealmInstant()

internal fun RealmInstant.asBsonDateTime() = BsonDateTime(toDuration().inWholeMilliseconds)
