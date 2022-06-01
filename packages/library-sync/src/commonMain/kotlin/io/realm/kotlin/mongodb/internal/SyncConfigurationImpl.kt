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

package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.interop.RealmConfigurationPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncSessionPointer
import io.realm.kotlin.internal.interop.SyncErrorCallback
import io.realm.kotlin.internal.interop.sync.PartitionValue
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsCallback
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession

internal class SyncConfigurationImpl(
    private val configuration: io.realm.kotlin.internal.InternalConfiguration,
    internal val partitionValue: PartitionValue?,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    val initialSubscriptionsCallback: InitialSubscriptionsCallback?,
    val rerunInitialSubscriptions: Boolean

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
            val nativeSyncConfig = if (partitionValue == null) {
                RealmInterop.realm_flx_sync_config_new(user.nativePointer)
            } else {
                RealmInterop.realm_sync_config_new(user.nativePointer, partitionValue.asSyncPartition())
            }

            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )
            RealmInterop.realm_config_set_sync_config(nativeConfig, nativeSyncConfig)
            nativeConfig
        }
    }

    override val syncMode: SyncMode =
        if (partitionValue == null) SyncMode.FLEXIBLE else SyncMode.PARTITION_BASED
}
