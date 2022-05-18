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
import io.realm.internal.interop.FrozenRealmPointer
import io.realm.internal.interop.LiveRealmPointer
import io.realm.internal.interop.RealmConfigurationPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSyncConfigurationPointer
import io.realm.internal.interop.RealmSyncSessionPointer
import io.realm.internal.interop.SyncAfterClientResetHandler
import io.realm.internal.interop.SyncBeforeClientResetHandler
import io.realm.internal.interop.SyncErrorCallback
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.interop.sync.SyncError
import io.realm.internal.interop.sync.SyncSessionResyncMode
import io.realm.internal.platform.freeze
import io.realm.internal.RealmImpl
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.mongodb.sync.SyncClientResetStrategy
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncSession

internal class SyncConfigurationImpl(
    private val configuration: InternalConfiguration,
    internal val partitionValue: PartitionValue,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    override val syncClientResetStrategy: SyncClientResetStrategy
) : InternalConfiguration by configuration, SyncConfiguration {

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val ptr: RealmConfigurationPointer = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val errorCallback = object : SyncErrorCallback {
            override fun onSyncError(pointer: RealmSyncSessionPointer, error: SyncError) {
                val session = SyncSessionImpl(pointer)
                val syncError = convertSyncError(error)
                // syncClientResetStrategy
                userErrorHandler.onError(session, syncError)
            }
        }.freeze()

        syncInitializer = { nativeConfig: RealmConfigurationPointer ->
            val nativeSyncConfig: RealmSyncConfigurationPointer =
                RealmInterop.realm_sync_config_new(
                    user.nativePointer,
                    partitionValue.asSyncPartition()
                )

            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )

            val clientResetMode: SyncSessionResyncMode = when (syncClientResetStrategy) {
                is ManuallyRecoverUnsyncedChangesStrategy ->
                    SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL
                is DiscardUnsyncedChangesStrategy ->
                    SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL
                else -> throw IllegalArgumentException("Invalid client reset type.")
            }
            RealmInterop.realm_sync_config_set_resync_mode(nativeSyncConfig, clientResetMode)

            // Set before and after handlers only if resync mode is not set to manual
            if (clientResetMode == SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL) {
                val onBefore: SyncBeforeClientResetHandler = object : SyncBeforeClientResetHandler {
                    override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                        // TODO figure out how to instantiate a realm with a frozen pointer
                        // val beforeInstance = RealmImpl(this@SyncConfigurationImpl, )
                        // (syncClientResetStrategy as DiscardUnsyncedChangesStrategy).onBeforeReset()
                    }
                }
                RealmInterop.realm_sync_config_set_before_client_reset_handler(
                    nativeSyncConfig,
                    onBefore
                )

                val onAfter: SyncAfterClientResetHandler = object : SyncAfterClientResetHandler {
                    override fun onAfterReset(
                        realmBefore: FrozenRealmPointer,
                        realmAfter: LiveRealmPointer,
                        didRecover: Boolean
                    ) {
                        // TODO figure out how to instantiate a realm with a frozen pointer
                    }
                }
                RealmInterop.realm_sync_config_set_after_client_reset_handler(
                    nativeSyncConfig,
                    onAfter
                )
            }

            RealmInterop.realm_config_set_sync_config(nativeConfig, nativeSyncConfig)

            nativeConfig
        }
    }
}
