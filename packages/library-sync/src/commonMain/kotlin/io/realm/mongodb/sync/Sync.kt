/*
 * Copyright 2020 Realm Inc.
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
package io.realm.mongodb.sync

import io.realm.mongodb.App

/**
 * A *sync* manager handling synchronization of local Realms with remote Realm Apps.
 *
 *
 * The primary role of this is to access the [SyncSession] for a synchronized Realm. After
 * opening the synchronized Realm you can access the [SyncSession] and perform synchronization
 * related operations as shown below:
 * <pre>
 * App app = new App("app-id");
 * User user = app.login(Credentials.anonymous());
 * SyncConfiguration syncConfiguration = new SyncConfiguration.Builder(user, "&lt;partition value&gt;")
 * .build();
 * Realm instance = Realm.getInstance(syncConfiguration);
 * SyncSession session = app.getSync().getSession(syncConfiguration);
 *
 * instance.executeTransaction(realm -&gt; {
 * realm.insert(...);
 * });
 * session.uploadAllLocalChanges();
 * instance.close();
</pre> *
 *
 * @see App.getSync
 * @see Sync.getSession
 */
interface Sync {

    /**
     * Gets a cached [SyncSession] for the given [SyncConfiguration] or throw if no one exists yet.
     *
     * A session should exist after you open a Realm with a [SyncConfiguration].
     *
     * @param syncConfiguration configuration object for the synchronized Realm.
     * @return the [SyncSession] for the specified Realm.
     * @throws IllegalArgumentException if syncConfiguration is `null`.
     * @throws IllegalStateException if the session could not be found using the provided `SyncConfiguration`.
     */
    fun getSession(syncConfiguration: SyncConfiguration): SyncSession

    /**
     * Gets a collection of all the cached [SyncSession].
     *
     * @return a collection of [SyncSession].
     */
    fun allSessions(): List<SyncSession>
}