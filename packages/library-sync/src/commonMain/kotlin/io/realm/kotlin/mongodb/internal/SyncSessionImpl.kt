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

import io.realm.kotlin.internal.NotificationToken
import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.interop.CoreError
import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncSessionPointer
import io.realm.kotlin.internal.interop.SyncSessionTransferCompletionCallback
import io.realm.kotlin.internal.interop.sync.CoreConnectionState
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.ProgressDirection
import io.realm.kotlin.internal.interop.sync.SyncError
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.internal.util.trySendWithBufferOverflowCheck
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.ConnectionState
import io.realm.kotlin.mongodb.sync.ConnectionStateChange
import io.realm.kotlin.mongodb.sync.Direction
import io.realm.kotlin.mongodb.sync.Progress
import io.realm.kotlin.mongodb.sync.ProgressMode
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import io.realm.kotlin.notifications.internal.Cancellable
import io.realm.kotlin.notifications.internal.Cancellable.Companion.NO_OP_NOTIFICATION_TOKEN
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal open class SyncSessionImpl(
    initializerRealm: RealmImpl?,
    internal val nativePointer: RealmSyncSessionPointer
) : SyncSession {

    // Constructor used when there is no Realm available, e.g. in the SyncSessionErrorHandler.
    // Without a Realm reference, it is impossible to track shared state between the public
    // Realm and the SyncSession. This impacts `downloadAllServerChanges()`.
    // Since there probably isn't a use case where you ever is going to call
    // `downloadAllServerChanges` inside the error handler, we are just going to disallow it by
    // throwing an IllegalStateException. Mostly because that is by far the easiest with the
    // current implementation.
    constructor(ptr: RealmSyncSessionPointer) : this(null, ptr)

    private val _realm: RealmImpl? = initializerRealm
    private val realm: RealmImpl
        get() = _realm ?: throw IllegalStateException("Operation is not allowed inside a `SyncSession.ErrorHandler`.")

    override val configuration: SyncConfiguration
        // TODO Get the sync config w/o ever throwing
        get() = realm.configuration as SyncConfiguration

    override val user: User
        get() = configuration.user

    override val state: SyncSession.State
        get() {
            val state = RealmInterop.realm_sync_session_state(nativePointer)
            return SyncSessionImpl.stateFrom(state)
        }

    override val connectionState: ConnectionState
        get() = connectionStateFrom(RealmInterop.realm_sync_connection_state(nativePointer))

    private enum class TransferDirection {
        UPLOAD, DOWNLOAD
    }

    override suspend fun downloadAllServerChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.DOWNLOAD, timeout)
    }

    override suspend fun uploadAllLocalChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.UPLOAD, timeout)
    }

    override fun pause() {
        RealmInterop.realm_sync_session_pause(nativePointer)
    }

    override fun resume() {
        RealmInterop.realm_sync_session_resume(nativePointer)
    }

    @Suppress("invisible_member", "invisible_reference")
    override fun progressAsFlow(
        direction: Direction,
        progressMode: ProgressMode,
    ): Flow<Progress> {
        return realm.scopedFlow {
            callbackFlow {
                val token: AtomicRef<Cancellable> =
                    kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
                token.value = NotificationToken(
                    RealmInterop.realm_sync_session_register_progress_notifier(
                        nativePointer,
                        when (direction) {
                            Direction.DOWNLOAD -> ProgressDirection.RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD
                            Direction.UPLOAD -> ProgressDirection.RLM_SYNC_PROGRESS_DIRECTION_UPLOAD
                        },
                        progressMode == ProgressMode.INDEFINITELY
                    ) { progressEstimate: Double ->
                        val progress = Progress(progressEstimate)
                        trySendWithBufferOverflowCheck(progress)
                        if (progressMode == ProgressMode.CURRENT_CHANGES && progress.isTransferComplete) {
                            close()
                        }
                    }
                )
                awaitClose {
                    token.value.cancel()
                }
            }
        }
    }

    @Suppress("invisible_member", "invisible_reference") // To be able to use RealmImpl.scopedFlow from library-base
    override fun connectionStateAsFlow(): Flow<ConnectionStateChange> = realm.scopedFlow {
        callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            token.value = NotificationToken(
                RealmInterop.realm_sync_session_register_connection_state_change_callback(
                    nativePointer
                ) { oldState: CoreConnectionState, newState: CoreConnectionState ->
                    trySendWithBufferOverflowCheck(
                        ConnectionStateChange(
                            connectionStateFrom(oldState),
                            connectionStateFrom(newState)
                        )
                    )
                }
            )
            awaitClose {
                token.value.cancel()
            }
        }
    }

    /**
     * Simulates a sync error. Internal visibility only for testing.
     */
    internal fun simulateSyncError(
        error: ErrorCode,
        message: String = "Simulate Client Reset"
    ) {
        RealmInterop.realm_sync_session_handle_error_for_testing(
            nativePointer,
            error,
            message,
            true
        )
    }

    /**
     * Wrap Core callbacks that will not be invoked until data has been either fully uploaded
     * or downloaded.
     *
     * When this method returns. The user facing Realm has been updated to the latest state.
     *
     * @param direction whether data is being uploaded or downloaded.
     * @param timeout timeout parameter.
     * @return `true` if the job completed before the timeout was hit, `false` otherwise.
     */
    private suspend fun waitForChanges(direction: TransferDirection, timeout: Duration): Boolean {
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                withContext(realm.notificationScheduler.dispatcher) {
                    val callback = object : SyncSessionTransferCompletionCallback {
                        override fun invoke(errorCode: CoreError?) {
                            if (errorCode != null) {
                                // Transform the errorCode into a dummy syncError so we can have a
                                // common path.
                                val syncError = SyncError(errorCode)
                                channel.trySend(convertSyncError(syncError))
                            } else {
                                channel.trySend(true)
                            }
                        }
                    }
                    when (direction) {
                        TransferDirection.UPLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_upload_completion(
                                nativePointer,
                                callback
                            )
                        }
                        TransferDirection.DOWNLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_download_completion(
                                nativePointer,
                                callback
                            )
                        }
                    }
                    channel.receive()
                }
            }
            // We need to refresh the public Realm when downloading to make the changes visible
            // to users immediately, this include functionality like `Realm.writeCopyTo()` which
            // require that all changes are uploaded.
            realm.refresh()
            when (result) {
                is Boolean -> return result
                is Throwable -> throw result
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            // However, since the download might have made progress and integrated some changesets,
            // we should still refresh the public facing Realm, so it reflect however far
            // Sync has gotten.
            realm.refresh()
            return false
        } finally {
            channel.close()
        }
    }

    fun close() {
        nativePointer.release()
    }

    internal companion object {
        internal fun stateFrom(coreState: CoreSyncSessionState): SyncSession.State {
            return when (coreState) {
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_DYING -> SyncSession.State.DYING
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_ACTIVE -> SyncSession.State.ACTIVE
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_INACTIVE -> SyncSession.State.INACTIVE
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN -> SyncSession.State.WAITING_FOR_ACCESS_TOKEN
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_PAUSED -> SyncSession.State.PAUSED
                else -> throw IllegalStateException("Unsupported state: $coreState")
            }
        }
        internal fun connectionStateFrom(coreConnectionState: CoreConnectionState): ConnectionState {
            return when (coreConnectionState) {
                CoreConnectionState.RLM_SYNC_CONNECTION_STATE_DISCONNECTED -> ConnectionState.DISCONNECTED
                CoreConnectionState.RLM_SYNC_CONNECTION_STATE_CONNECTING -> ConnectionState.CONNECTING
                CoreConnectionState.RLM_SYNC_CONNECTION_STATE_CONNECTED -> ConnectionState.CONNECTED
                else -> throw IllegalStateException("Unsupported connection state: $coreConnectionState")
            }
        }
    }
}
