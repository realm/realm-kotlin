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

package io.realm.kotlin.mongodb.exceptions

import io.realm.kotlin.internal.interop.RealmAppPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.sync.SyncError

/**
 * Class encapsulating information needed for handling a Client Reset event.
 *
 * **See:** [Client Reset](https://www.mongodb.com/docs/atlas/app-services/sync/error-handling/client-resets/)
 * for more information about when and why Client Reset occurs and how to deal with it.
 *
 */
public class ClientResetRequiredException constructor(
    private val appPointer: RealmAppPointer,
    private val error: SyncError
) : Throwable(message = error.detailedMessage) {

    public val originalFilePath: String = error.originalFilePath!!
    public val recoveryFilePath: String = error.recoveryFilePath!!

    /**
     * Calling this method will execute the Client Reset manually instead of waiting until the next
     * app restart.
     *
     * After this method returns, the backup file can be found in the location returned by
     * [recoveryFilePath]. The file at [originalFilePath] will have been deleted, but will be
     * recreated from scratch next time a Realm instance is opened.
     *
     * **WARNING:** To guarantee the backup file is generated correctly all Realm instances
     * associated to the session in which this error is generated **must be closed**. Not doing so
     * might result in unexpected file system errors.
     *
     * @throws IllegalStateException if not all instances have been closed.
     */
    public fun executeClientReset() {
        RealmInterop.realm_sync_immediately_run_file_actions(appPointer, originalFilePath)
    }
}
