/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.realm

import io.realm.schema.RealmSchema

/**
 * Base class for all Realm instances ([Realm] and [MutableRealm]).
 */
public interface BaseRealm : Versioned {
    /**
     * Configuration used to configure this Realm instance.
     */
    public val configuration: Configuration

    /**
     * Returns an immutable schema of the realm.
     *
     * @return the schema of the realm.
     */
    public fun schema(): RealmSchema

    /**
     * Returns the schema version of the realm.
     *
     * The default initial schema version is 0.
     *
     * @return the schema version of the realm.
     *
     * @see [Configuration.SharedBuilder.schemaVersion]
     */
    public fun schemaVersion(): Long

    /**
     * Returns the current number of active versions in the Realm file. A large number of active versions can have
     * a negative impact on the Realm file size on disk.
     *
     * @see [Configuration.Builder.maxNumberOfActiveVersions]
     */
    public fun getNumberOfActiveVersions(): Long

    /**
     * Check if this Realm has been closed or not. If the Realm has been closed, most methods
     * will throw [IllegalStateException] if called.
     *
     * @return `true` if the Realm has been closed. `false` if not.
     */
    public fun isClosed(): Boolean
}
