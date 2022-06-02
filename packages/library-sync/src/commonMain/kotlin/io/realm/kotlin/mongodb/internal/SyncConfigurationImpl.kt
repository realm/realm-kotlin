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

import io.realm.internal.SimpleFrozenRealmImpl
import io.realm.internal.SimpleLiveRealmImpl
import io.realm.kotlin.internal.InternalConfiguration
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.interop.FrozenRealmPointer
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.interop.RealmConfigurationPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncConfigurationPointer
import io.realm.kotlin.internal.interop.RealmSyncSessionPointer
import io.realm.kotlin.internal.interop.SyncAfterClientResetHandler
import io.realm.kotlin.internal.interop.SyncBeforeClientResetHandler
import io.realm.kotlin.internal.interop.SyncErrorCallback
import io.realm.kotlin.internal.interop.sync.PartitionValue
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.InitialRemoteDataConfiguration
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsConfiguration
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession
import io.realm.mongodb.sync.ClientResetRequiredException
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.mongodb.sync.SyncClientResetStrategy

@Suppress("LongParameterList")
internal class SyncConfigurationImpl(
    private val configuration: io.realm.kotlin.internal.InternalConfiguration,
    internal val partitionValue: PartitionValue?,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    override val syncClientResetStrategy: SyncClientResetStrategy,
    override val initialSubscriptions: InitialSubscriptionsConfiguration?,
    override val initialRemoteData: InitialRemoteDataConfiguration?
) : InternalConfiguration by configuration, SyncConfiguration {

    override suspend fun realmOpened(realm: RealmImpl, fileCreated: Boolean) {
        initialSubscriptions?.let { initialSubscriptionsConfig ->
            if (initialSubscriptionsConfig.rerunOnOpen || fileCreated) {
                realm.subscriptions.update {
                    with(initialSubscriptions.callback) {
                        write(realm)
                    }
                }
            }
        }
        if (initialRemoteData != null && (fileCreated || initialSubscriptions?.rerunOnOpen == true)) {
            val success: Boolean = if (initialSubscriptions != null) {
                realm.subscriptions.waitForSynchronization(initialRemoteData.timeout)
            } else {
                realm.syncSession.downloadAllServerChanges(initialRemoteData.timeout)
            }
            if (!success) {
                throw DownloadingRealmTimeOutException(this)
            }
        }
        configuration.realmOpened(realm, fileCreated)
    }

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val ptr: RealmConfigurationPointer = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val resetStrategy = syncClientResetStrategy.freeze()
        val frozenAppPointer = user.app.nativePointer.freeze()
        val errorCallback = object : SyncErrorCallback {
            override fun onSyncError(pointer: RealmSyncSessionPointer, error: SyncError) {
                val session = SyncSessionImpl(pointer)
                val syncError = convertSyncError(error)

                // Notify before/after callbacks too if error is client reset
                if (error.isClientResetRequested) {
                    when (resetStrategy) {
                        is DiscardUnsyncedChangesStrategy -> resetStrategy.onError(
                            session,
                            ClientResetRequiredException(
                                frozenAppPointer,
                                error
                            )
                        )
                        is ManuallyRecoverUnsyncedChangesStrategy -> resetStrategy.onClientReset(
                            session,
                            ClientResetRequiredException(
                                frozenAppPointer,
                                error
                            )
                        )
                        else -> throw IllegalArgumentException("Unsupported client reset strategy.")
                    }
                }

                userErrorHandler.onError(session, syncError)
            }
        }.freeze()

        syncInitializer = { nativeConfig: RealmConfigurationPointer ->
            val nativeSyncConfig: RealmSyncConfigurationPointer = if (partitionValue == null) {
                RealmInterop.realm_flx_sync_config_new(user.nativePointer)
            } else {
                RealmInterop.realm_sync_config_new(user.nativePointer, partitionValue.asSyncPartition())
            }

            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )

            val clientResetMode: SyncSessionResyncMode = when (resetStrategy) {
                is ManuallyRecoverUnsyncedChangesStrategy ->
                    SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL
                is DiscardUnsyncedChangesStrategy ->
                    SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL
                else -> throw IllegalArgumentException("Invalid client reset type: $resetStrategy")
            }
            RealmInterop.realm_sync_config_set_resync_mode(nativeSyncConfig, clientResetMode)

            // Set before and after handlers only if resync mode is set to discard local
            if (clientResetMode == SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL) {
                val onBefore: SyncBeforeClientResetHandler = object : SyncBeforeClientResetHandler {
                    override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                        (resetStrategy as DiscardUnsyncedChangesStrategy).onBeforeReset(
                            SimpleFrozenRealmImpl(realmBefore, configuration)
                        )
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
                        // Needed to allow writes on the Mutable after Realm
                        RealmInterop.realm_begin_write(realmAfter)

                        (resetStrategy as DiscardUnsyncedChangesStrategy).onAfterReset(
                            SimpleFrozenRealmImpl(realmBefore, configuration),
                            SimpleLiveRealmImpl(realmAfter, configuration)
                        )

                        // Transaction might have been reverted check if we can commit
                        if (RealmInterop.realm_is_in_transaction(realmAfter)) {
                            RealmInterop.realm_commit(realmAfter)
                        }
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

    override val syncMode: SyncMode =
        if (partitionValue == null) SyncMode.FLEXIBLE else SyncMode.PARTITION_BASED
}
