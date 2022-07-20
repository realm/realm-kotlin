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
import io.realm.kotlin.internal.interop.FrozenRealmPointer
import io.realm.kotlin.internal.interop.LiveRealmPointer
import io.realm.kotlin.internal.interop.RealmAppPointer
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
import io.realm.kotlin.mongodb.sync.PartitionValue
import io.realm.kotlin.mongodb.sync.RecoverOrDiscardUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.RecoverUnsyncedChangesStrategy
import io.realm.kotlin.mongodb.sync.SyncClientResetStrategy
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncMode
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.mongodb.syncSession

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

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

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
            is RecoverUnsyncedChangesStrategy ->
                RecoverUnsyncedChangesHelper(resetStrategy, configuration)
            is RecoverOrDiscardUnsyncedChangesStrategy ->
                RecoverOrDiscardUnsyncedChangesHelper(resetStrategy, configuration)
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
            val nativeSyncConfig: RealmSyncConfigurationPointer = when (partitionValue) {
                null -> RealmInterop.realm_flx_sync_config_new(user.nativePointer)
                else -> RealmInterop.realm_sync_config_new(
                    user.nativePointer,
                    partitionValue.asSyncPartition()
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
}

/**
 * TODO
 */
private interface ClientResetStrategyHelper {
    fun initialize(nativeSyncConfig: RealmSyncConfigurationPointer)
    fun onSyncError(session: SyncSession, appPointer: RealmAppPointer, error: SyncError)
}

/**
 * TODO
 */
private abstract class OnBeforeOnAfterHelper<T : SyncClientResetStrategy> constructor(
    val strategy: T,
    val configuration: InternalConfiguration
) : ClientResetStrategyHelper {

    abstract fun getResyncMode(): SyncSessionResyncMode
    abstract fun getBefore(): SyncBeforeClientResetHandler
    abstract fun getAfter(): SyncAfterClientResetHandler

    override fun initialize(nativeSyncConfig: RealmSyncConfigurationPointer) {
        RealmInterop.realm_sync_config_set_resync_mode(nativeSyncConfig, getResyncMode())
        RealmInterop.realm_sync_config_set_before_client_reset_handler(
            nativeSyncConfig,
            getBefore()
        )
        RealmInterop.realm_sync_config_set_after_client_reset_handler(
            nativeSyncConfig,
            getAfter()
        )
    }
}

/**
 * TODO
 */
private class RecoverOrDiscardUnsyncedChangesHelper constructor(
    strategy: RecoverOrDiscardUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<RecoverOrDiscardUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_RECOVER_OR_DISCARD

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                strategy.onBeforeReset(TypedFrozenRealmImpl(realmBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
            override fun onAfterReset(
                realmBefore: FrozenRealmPointer,
                realmAfter: LiveRealmPointer,
                didRecover: Boolean
            ) {
                // Needed to allow writes on the Mutable after Realm
                RealmInterop.realm_begin_write(realmAfter)

                @Suppress("TooGenericExceptionCaught")
                try {
                    val before = TypedFrozenRealmImpl(realmBefore, configuration)
                    val after = MutableLiveRealmImpl(realmAfter, configuration)
                    if (didRecover) {
                        strategy.onAfterRecovery(before, after)
                    } else {
                        strategy.onAfterDiscard(before, after)
                    }

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

/**
 * TODO
 */
private class RecoverUnsyncedChangesHelper constructor(
    strategy: RecoverUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<RecoverUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_RECOVER

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                strategy.onBeforeReset(TypedFrozenRealmImpl(realmBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
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

/**
 * TODO
 */
private class DiscardUnsyncedChangesHelper constructor(
    strategy: DiscardUnsyncedChangesStrategy,
    configuration: InternalConfiguration
) : OnBeforeOnAfterHelper<DiscardUnsyncedChangesStrategy>(strategy, configuration) {

    override fun getResyncMode(): SyncSessionResyncMode =
        SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL

    override fun getBefore(): SyncBeforeClientResetHandler =
        object : SyncBeforeClientResetHandler {
            override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                strategy.onBeforeReset(TypedFrozenRealmImpl(realmBefore, configuration))
            }
        }

    override fun getAfter(): SyncAfterClientResetHandler =
        object : SyncAfterClientResetHandler {
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

/**
 * TODO
 */
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
