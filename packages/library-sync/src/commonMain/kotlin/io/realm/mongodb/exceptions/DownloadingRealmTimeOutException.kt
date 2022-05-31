/*
 * Copyright 2022 Realm Inc.
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

package io.realm.mongodb.exceptions

import io.realm.exceptions.RealmException
import io.realm.mongodb.sync.SyncConfiguration

/**
 * Thrown when opening a Realm and it didn't finish download server data in the allocated timeframe.
 *
 * This can only happen if [SyncConfiguration.Builder.waitForInitialRemoteData] is set.
 */
public class DownloadingRealmTimeOutException : RealmException {
    internal constructor(syncConfig: SyncConfiguration) : super(
        "Realm did not managed to download all initial data in time: ${syncConfig.path}, " +
            "timeout: ${syncConfig.initialRemoteData?.timeout ?: "<missing>"}."
    )
}
