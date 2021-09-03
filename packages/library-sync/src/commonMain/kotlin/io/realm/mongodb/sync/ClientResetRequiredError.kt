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

import io.realm.DynamicRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.annotations.Beta
import io.realm.mongodb.AppException
import io.realm.mongodb.ErrorCode
import java.io.File

/**
 * Class encapsulating information needed for handling a Client Reset event.
 *
 * @see SyncSession.ErrorHandler.onError
 */
@Beta
class ClientResetRequiredError internal constructor(
    appNativePointer: Long,
    errorCode: ErrorCode?,
    errorMessage: String?,
    originalConfiguration: io.realm.mongodb.sync.SyncConfiguration,
    backupConfiguration: RealmConfiguration
) : AppException(errorCode, errorMessage) {
    private val appNativePointer: Long
    private val originalConfiguration: io.realm.mongodb.sync.SyncConfiguration
    private val backupConfiguration: RealmConfiguration

    /**
     * Returns the location of the backed up Realm file. The file will not be present until the Client Reset has been
     * fully executed.
     *
     * @return a reference to the location of the backup file once Client Reset has been executed.
     * Use `file.exists()` to check if the file exists or not.
     */
    val backupFile: File

    /**
     * Returns the location of the original Realm file. After the Client Reset has completed, the file at this location
     * will be deleted.
     *
     * @return a reference to the location of the original Realm file. After Client Reset has been executed this file
     * will no longer exists. Use `file.exists()` to check this.
     */
    val originalFile: File

    /**
     * Calling this method will execute the Client Reset manually instead of waiting until next app restart. This will
     * only be possible if all instances of that Realm have been closed, otherwise a [IllegalStateException] will
     * be thrown.
     *
     *
     * After this method returns, the backup file can be found in the location returned by [.getBackupFile].
     * The file at [.getOriginalFile] have been deleted, but will be recreated from scratch next time a
     * Realm instance is opened.
     *
     * @throws IllegalStateException if not all instances have been closed.
     */
    fun executeClientReset() {
        synchronized(Realm::class.java) {
            check(Realm.getGlobalInstanceCount(originalConfiguration) <= 0) {
                "Realm has not been fully closed. Client Reset cannot run before all " +
                        "instances have been closed."
            }
            nativeExecuteClientReset(appNativePointer, originalConfiguration.getPath())
        }
    }

    /**
     * The configuration that can be used to open the backup Realm offline. This configuration can
     * only be used in combination with a [DynamicRealm].
     *
     * @return the configuration that can be used to open the backup Realm offline.
     */
    val backupRealmConfiguration: RealmConfiguration
        get() = backupConfiguration

    // PRECONDITION: All Realm instances for this path must have been closed.
    private external fun nativeExecuteClientReset(appNativePointer: Long, originalPath: String)

    init {
        this.originalConfiguration = originalConfiguration
        this.backupConfiguration = backupConfiguration
        backupFile = File(backupConfiguration.getPath())
        originalFile = File(originalConfiguration.getPath())
        this.appNativePointer = appNativePointer
    }
}