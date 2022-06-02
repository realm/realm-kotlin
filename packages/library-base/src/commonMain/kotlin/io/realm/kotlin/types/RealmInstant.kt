/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.realm.kotlin.types

import io.realm.kotlin.internal.RealmInstantImpl

/**
 * A representation of a Realm timestamp. A timestamp represent a single point in time defined as
 * the distance from the UNIX epoch: 00:00:00 UTC on 1 January 1970, expressed in seconds and
 * nanoseconds.
 *
 * Specifically, this means that all timestamps after the epoch consist of positive numbers
 * and all timestamps before the epoch consists of the negative numbers.
 *
 * Examples:
 * - The UNIX epoch is constructed by `RealmInstant.from(0, 0)`.
 * - Relative times are constructed as follows:
 *      - +1 second is constructed by RealmInstant.from(1, 0)
 *      - +1 nanosecond is constructed by RealmInstant.from(0, 1)
 *      - +1.1 seconds (1100 milliseconds after the epoch) is constructed by RealmInstant.from(1, 100000000)
 *      - -1.1 seconds (1100 milliseconds before the epoch) is constructed by RealmInstant.from(-1, -100000000)
 */
public interface RealmInstant : Comparable<RealmInstant> {

    public companion object {
        private const val SEC_AS_NANOSECOND: Int = 1_000_000_000

        /**
         * Minimum timestamp that can be stored in Realm.
         */
        public val MIN: RealmInstant = from(Long.MIN_VALUE, -999_999_999)

        /**
         * Maximum timestamp that can be stored in Realm.
         */
        public val MAX: RealmInstant = from(Long.MAX_VALUE, 999_999_999)

        /**
         * Creates a [RealmInstant] that is the [epochSeconds] number of seconds from the UNIX epoch
         * `1970-01-01T00:00:00Z` and the [nanosecondAdjustment] number of nanoseconds from the
         * whole second.
         *
         * All timestamps after the epoch consist of positive numbers and all timestamps before the
         * epoch consists of the negative numbers.
         *
         * If [nanosecondAdjustment] is bigger than `999_999_999` or smaller than `-999_999_999`,
         * the value will increment or decrement [epochSeconds] accordingly until
         * [nanosecondsOfSecond] is within the valid range.
         *
         * If the timestamp exceed the maximal bounds of [epochSeconds], the Timestamp will clamp
         * to either [MIN] or [MAX].
         */
        public fun from(epochSeconds: Long, nanosecondAdjustment: Int): RealmInstant {
            val secAdjustment: Long = (nanosecondAdjustment / SEC_AS_NANOSECOND).toLong()
            val nsAdjustment: Int = nanosecondAdjustment % SEC_AS_NANOSECOND
            var s: Long = epochSeconds + secAdjustment
            var ns: Int = nsAdjustment
            if (((epochSeconds.xor(s)).and(secAdjustment.xor(s))) < 0) {
                return if (epochSeconds < 0) MIN else MAX
            }
            return RealmInstantImpl(s, ns)
        }
    }

    /**
     * The number of seconds from the epoch `1970-01-01T00:00:00Z` rounded down to a [Long]
     * number.
     *
     * The difference between the rounded number of seconds and the actual number of seconds
     * is returned by [nanosecondsOfSecond] property expressed in nanoseconds.
     */
    public val epochSeconds: Long

    /**
     * The number of nanoseconds by which this instant is different than [epochSeconds].
     *
     * The value always lies in the range `-999_999_999..999_999_999`.
     */
    public val nanosecondsOfSecond: Int
}
