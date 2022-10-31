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
import io.realm.kotlin.internal.MutableLiveRealmImpl
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.TypedFrozenRealmImpl
import io.realm.kotlin.internal.interop.AsyncOpenCallback
import io.realm.kotlin.internal.interop.FrozenRealmPointer
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.interop.RealmAppPointer
import io.realm.kotlin.internal.interop.RealmAsyncOpenTaskPointer
import io.realm.kotlin.internal.interop.RealmConfigurationPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncConfigurationPointer
import io.realm.kotlin.internal.interop.RealmSyncSessionPointer
import io.realm.kotlin.internal.interop.SyncAfterClientResetHandler
import io.realm.kotlin.internal.interop.SyncBeforeClientResetHandler
import io.realm.kotlin.internal.interop.SyncErrorCallback
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.interop.sync.SyncSessionResyncMode
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.mongodb.exceptions.ClientResetRequiredException
import io.realm.kotlin.mongodb.exceptions.DownloadingRealmTimeOutException
import io.realm.kotlin.mongodb.subscriptions
import io.realm.kotlin.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.InitialRemoteDataConfiguration
import io.realm.kotlin.mongodb.sync.InitialSubscriptionsConfiguration
import io.realm.kotlin.mongodb.sync.ManuallyRecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncClientResetStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.mongodb.kbson.BsonValue

@Suppress("LongParameterList")
internal class SyncConfigurationImpl(
    private val configuration: InternalConfiguration,
    internal val partitionValue: BsonValue?,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    override val syncClientResetStrategy: SyncClientResetStrategy,
    override val initialSubscriptions: InitialSubscriptionsConfiguration?,
    override val initialRemoteData: InitialRemoteDataConfiguration?
) : InternalConfiguration by configuration, SyncConfiguration {

    override suspend fun openRealm(realm: RealmImpl): Pair<LiveRealmPointer, Boolean> {
        // Partition-based Realms with `waitForInitialRemoteData` enabled will use
        // async open first do download the server side Realm. This is much much faster than
        // creating the Realm locally first and then downloading (and integrating) changes into
        // that.
        if (partitionValue != null && initialRemoteData != null) {
            // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
            // that results in the `Continuation` being frozen, which breaks it.
            val channel = Channel<Any>(1)
            val taskPointer: AtomicRef<RealmAsyncOpenTaskPointer?> = atomic(null)
            try {
                val result: Any = withTimeout(initialRemoteData.timeout.inWholeMilliseconds) {
                    withContext(realm.notificationDispatcherHolder.dispatcher) {
                        val callback = AsyncOpenCallback { error: Throwable? ->
                            if (error != null) {
                                channel.trySend(error)
                            } else {
                                channel.trySend(true)
                            }
                        }.freeze()

                        val configPtr = createNativeConfiguration()
                        taskPointer.value = RealmInterop.realm_open_synchronized(configPtr)
                        RealmInterop.realm_async_open_task_start(taskPointer.value!!, callback)
                        channel.receive()
                    }
                }
                when (result) {
                    is Boolean -> { /* Do nothing, opening the Realm will follow below */ }
                    is Throwable -> throw result
                    else -> throw IllegalStateException("Unexpected value: $result")
                }
            } catch (ex: TimeoutCancellationException) {
                taskPointer.value?.let { ptr: RealmAsyncOpenTaskPointer ->
                    RealmInterop.realm_async_open_task_cancel(ptr)
                }
                throw DownloadingRealmTimeOutException(this)
            } finally {
                channel.close()
            }
        }

        // Open the local Realm file. This will also include any data potentially downloaded
        // by Async Open above.
        return configuration.openRealm(realm)
    }

    override suspend fun initializeRealmData(realm: RealmImpl, realmFileCreated: Boolean) {
        // Create or update subscriptions for Flexible Sync realms as needed.
        initialSubscriptions?.let { initialSubscriptionsConfig ->
            if (initialSubscriptionsConfig.rerunOnOpen || realmFileCreated) {
                realm.subscriptions.update {
                    with(initialSubscriptions.callback) {
                        write(realm)
                    }
                }
            }
        }

        // Download subscription data if needed. Partition-base realms can only configure
        // `waitForInitialRemoteData` which is being accounted for when calling `openRealm`, so that
        // case is ignored here.
        if (initialRemoteData != null && initialSubscriptions != null) {
            val updateExistingFile = initialSubscriptions.rerunOnOpen && !realmFileCreated
            if (realmFileCreated || updateExistingFile) {
                val success: Boolean =
                    realm.subscriptions.waitForSynchronization(initialRemoteData.timeout)
                if (!success) {
                    throw DownloadingRealmTimeOutException(this)
                }
            }
        }

        // Last, run any local Realm initialization logic
        configuration.initializeRealmData(realm, realmFileCreated)
    }

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val ptr: RealmConfigurationPointer = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    private interface ClientResetStrategyHelper {
        fun initialize(nativeSyncConfig: RealmSyncConfigurationPointer)
        fun onSyncError(session: SyncSession, appPointer: RealmAppPointer, error: SyncError)
    }

    private class DiscardUnsyncedChangesHelper constructor(
        val strategy: DiscardUnsyncedChangesStrategy,
        val configuration: InternalConfiguration
    ) : ClientResetStrategyHelper {
        override fun initialize(nativeSyncConfig: RealmSyncConfigurationPointer) {
            RealmInterop.realm_sync_config_set_resync_mode(
                nativeSyncConfig,
                SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL
            )

            val onBefore: SyncBeforeClientResetHandler = object : SyncBeforeClientResetHandler {
                override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                    strategy.onBeforeReset(TypedFrozenRealmImpl(realmBefore, configuration))
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

                    @Suppress("TooGenericExceptionCaught")
                    try {
                        strategy.onAfterReset(
                            TypedFrozenRealmImpl(realmBefore, configuration),
                            MutableLiveRealmImpl(realmAfter, configuration)
                        )

                        // Callback completed successfully we can safely commit the changes
                        // user might have cancelled the transaction manually
                        if (RealmInterop.realm_is_in_transaction(realmAfter)) {
                            RealmInterop.realm_commit(realmAfter)
                        }
                    } catch (exception: Throwable) {
                        // Cancel the transaction
                        // user might have cancelled the transaction manually
                        if (RealmInterop.realm_is_in_transaction(realmAfter)) {
                            RealmInterop.realm_rollback(realmAfter)
                        }
                        // Rethrow so core can send it over again
                        throw exception
                    }
                }
            }

            RealmInterop.realm_sync_config_set_after_client_reset_handler(
                nativeSyncConfig,
                onAfter
            )
        }

        override fun onSyncError(
            session: SyncSession,
            appPointer: RealmAppPointer,
            error: SyncError
        ) {
            // If there is a user exception we appoint it as the cause of the client reset
            strategy.onError(
                session,
                ClientResetRequiredException(appPointer, error)
            )
        }
    }

    private class ManuallyRecoverUnsyncedChangesHelper(
        val strategy: ManuallyRecoverUnsyncedChangesStrategy
    ) : ClientResetStrategyHelper {
        override fun initialize(nativeSyncConfig: RealmSyncConfigurationPointer) {
            RealmInterop.realm_sync_config_set_resync_mode(
                nativeSyncConfig,
                SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_MANUAL
            )
        }

        override fun onSyncError(
            session: SyncSession,
            appPointer: RealmAppPointer,
            error: SyncError
        ) {
            strategy.onClientReset(
                session,
                ClientResetRequiredException(appPointer, error)
            )
        }
    }

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val resetStrategy = syncClientResetStrategy.freeze()
        val frozenAppPointer = user.app.nativePointer.freeze()

        val initializerHelper = when (resetStrategy) {
            is DiscardUnsyncedChangesStrategy ->
                DiscardUnsyncedChangesHelper(resetStrategy, configuration)
            is ManuallyRecoverUnsyncedChangesStrategy ->
                ManuallyRecoverUnsyncedChangesHelper(resetStrategy)
            else -> throw IllegalArgumentException("Unsupported client reset strategy: $resetStrategy")
        }

        val errorCallback =
            SyncErrorCallback { pointer: RealmSyncSessionPointer, error: SyncError ->
                val session = SyncSessionImpl(pointer)
                val syncError = convertSyncError(error)

                // Notify before/after callbacks too if error is client reset
                if (error.isClientResetRequested) {
                    initializerHelper.onSyncError(session, frozenAppPointer, error)
                } else {
                    userErrorHandler.onError(session, syncError)
                }
            }.freeze()

        syncInitializer = { nativeConfig: RealmConfigurationPointer ->
            val nativeSyncConfig: RealmSyncConfigurationPointer = if (partitionValue == null) {
                RealmInterop.realm_flx_sync_config_new(user.nativePointer)
            } else {
                RealmInterop.realm_sync_config_new(
                    user.nativePointer,
                    partitionValue.toJson()
                )
            }

            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )

            // Do any initialization required for the strategies
            initializerHelper.initialize(nativeSyncConfig)

            RealmInterop.realm_config_set_sync_config(nativeConfig, nativeSyncConfig)

            nativeConfig
        }
    }

    override val syncMode: SyncMode =
        if (partitionValue == null) SyncMode.FLEXIBLE else SyncMode.PARTITION_BASED
}
