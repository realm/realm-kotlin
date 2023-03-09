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
package io.realm.kotlin

/**
 * A `VersionId` representing the transactional id of the Realm itself or it's objects.
 *
 * Realm is an [MVCC](https://en.wikipedia.org/wiki/Multiversion_concurrency_control) database. This means that at any
 * given time, multiple version of data can be visible. This class describes the version of such data.
 *
 * The version of a Realm will change whenever a write is committed.
 */
public data class VersionId public constructor(public val version: Long) : Comparable<VersionId> {

    init {
        // Realm Core exposes these numbers as uint64_t, but it would be REALLY surprising if this ever
        // overflowed in Kotlin. Instead of adding more complex logic to this or depend on experimental
        // ULong, just throw an exception. We can probably move to ULong support before ever hitting this
        // case.
        if (version < 0) {
            throw IllegalArgumentException("'version' must both be numbers >= 0. It was: $version")
        }
    }

    override fun compareTo(other: VersionId): Int {
        // index is only used internally by Core and isn't used when ordering versions.
        return when {
            version > other.version -> 1
            version < other.version -> -1
            else -> 0
        }
    }
}
