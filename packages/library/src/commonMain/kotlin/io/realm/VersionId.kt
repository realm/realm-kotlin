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
package io.realm

/**
 * Realm is an [MVCC](https://en.wikipedia.org/wiki/Multiversion_concurrency_control) database. This means that at any
 * given time, multiple version of data can be visible. This class describes the version of such data.
 *
 * The version of a Realm will change whenever a write is committed.
 */
// FIXME What should we call this. VersionId is a very internal name, but is `Version` to broad? Currently in
//  Realm Java, `Realm.getVersion()` returns the schema version. Should we have both `Realm.version` and
//  `RealmSchema.version()`?
@ExperimentalUnsignedTypes
public data class VersionId constructor(public val version: ULong, public val index: ULong): Comparable<VersionId> {
    // TODO: Figure out exactly how to do comparison. In Realm Java we just
    //  used version, but I assume index can also play a part somehow?
    override fun compareTo(other: VersionId): Int {
        return when {
            version > other.version -> 1
            version < other.version -> -1
            else -> 0
        }
    }
}