/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.internal

import io.realm.kotlin.VersionId

/**
 * A **live realm holder** encapsulated common properties of [SuspendableWriter] and
 * [SuspendableNotifier] for easier access to version information and GC-tracked snapshot
 * references when advancing the version of [RealmImpl].
 */
internal abstract class LiveRealmHolder<out LiveRealm> {

    abstract val realmInitializer: Lazy<LiveRealm>
    abstract val realm: io.realm.kotlin.internal.LiveRealm
    protected abstract val hasInitialRealm: Boolean

    /**
     * Current version of the frozen snapshot reference of the live realm. This is not guaranteed
     * to the same version as the actual live realm, but can be used to indicate that we can
     * request a more recent GC-tracked snapshot from the [LiveRealmHolder] through [snapshot].
     */
    val version: VersionId?
        get() = if (hasInitialRealm || realmInitializer.isInitialized()) { realm.snapshotVersion } else null

    /**
     * Returns a GC-tracked snapshot from the underlying [realm]. See [LiveRealm.gcTrackedSnapshot]
     * for details of the tracking.
     */
    val snapshot: FrozenRealmReference?
        get() = if (hasInitialRealm || realmInitializer.isInitialized()) {
            realm.gcTrackedSnapshot()
        } else null

    /**
     * Dump the current snapshot and tracked versions of the LiveRealm used for debugging purpose.
     */
    fun versions(): VersionData? = if (hasInitialRealm || realmInitializer.isInitialized()) {
        realm.versions()
    } else {
        null
    }
}
