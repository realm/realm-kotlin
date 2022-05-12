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

package io.realm.mongodb.internal

import io.realm.internal.InternalConfiguration
import io.realm.internal.interop.RealmConfigurationPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSyncConfigurationPointer
import io.realm.internal.interop.RealmSyncSessionPointer
import io.realm.internal.interop.SyncErrorCallback
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.interop.sync.SyncError
import io.realm.internal.platform.freeze
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession

internal class SyncConfigurationImpl(
    private val configuration: io.realm.internal.InternalConfiguration,
    internal val partitionValue: PartitionValue,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler
) : InternalConfiguration by configuration, SyncConfiguration {

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val ptr = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val errorCallback = object : SyncErrorCallback {
            override fun onSyncError(pointer: RealmSyncSessionPointer, error: SyncError) {
                userErrorHandler.onError(SyncSessionImpl(pointer), convertSyncError(error))
            }
        }.freeze()

        syncInitializer = { nativeConfig: RealmConfigurationPointer ->
            val nativeSyncConfig: RealmSyncConfigurationPointer = RealmInterop.realm_sync_config_new(
                user.nativePointer,
                partitionValue.asSyncPartition()
            )
            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )
            RealmInterop.realm_config_set_sync_config(nativeConfig, nativeSyncConfig)
            nativeConfig
        }
    }
}
