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
 * A RealmLifeCycle exposes common methods to query the state of any Realm object.
 */
// FIXME Should we have a public interface with a subset of these or split even further into
//  Closeable, etc.
internal interface RealmLifeCycle : Versioned {
    fun isFrozen(): Boolean
    fun isClosed(): Boolean
}

// Singleton instance acting as implementation for all unmanaged objects
object UnmanagedLifeCycle : RealmLifeCycle {
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

// Default implementation for all objects that can provide a realmLifeCycle instance
internal interface RealmLifeCycleHolder : RealmLifeCycle {
    fun realmLifeCycle(): RealmLifeCycle

    override fun version(): VersionId {
        return realmLifeCycle().version()
    }

    override fun isFrozen(): Boolean {
        return realmLifeCycle().isFrozen()
    }

    override fun isClosed(): Boolean {
        return realmLifeCycle().isClosed()
    }
}
