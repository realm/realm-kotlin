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

package io.realm.internal

import io.realm.VersionId
import io.realm.Versioned

/**
 * A RealmState exposes common methods to query the state of any Realm object.
 */
// TODO Public due to being a transitive dependency to RealmStateHolder
public interface RealmState : Versioned {
    public fun isFrozen(): Boolean
    public fun isClosed(): Boolean
}

// Singleton instance acting as implementation for all unmanaged objects
internal object UnmanagedState : RealmState {
    override fun version(): VersionId {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }

    override fun isFrozen(): Boolean {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }

    override fun isClosed(): Boolean {
        throw IllegalArgumentException("Cannot access life cycle information on unmanaged object")
    }
}

// Default implementation for all objects that can provide a RealmState instance
// TODO Public due to being a transitive dependency to RealmObjectInternal
public interface RealmStateHolder : RealmState {
    public fun realmState(): RealmState

    override fun version(): VersionId {
        return realmState().version()
    }

    override fun isFrozen(): Boolean {
        return realmState().isFrozen()
    }

    override fun isClosed(): Boolean {
        return realmState().isClosed()
    }
}
