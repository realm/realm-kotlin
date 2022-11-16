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

import io.realm.kotlin.internal.RealmImpl
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSyncSessionPointer
import io.realm.kotlin.internal.interop.SyncSessionTransferCompletionCallback
import io.realm.kotlin.internal.interop.sync.CoreSyncSessionState
import io.realm.kotlin.internal.interop.sync.ProtocolClientErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCode
import io.realm.kotlin.internal.interop.sync.SyncErrorCodeCategory
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.util.Validation
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.sync.SyncConfiguration
import io.realm.kotlin.mongodb.sync.SyncSession
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration

internal open class SyncSessionImpl(
    private val realm: RealmImpl?,
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

    private enum class TransferDirection {
        UPLOAD, DOWNLOAD
    }

    override suspend fun downloadAllServerChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.DOWNLOAD, timeout)
    }

    override suspend fun uploadAllLocalChanges(timeout: Duration): Boolean {
        return waitForChanges(TransferDirection.UPLOAD, timeout)
    }

    override val state: SyncSession.State
        get() {
            val state = RealmInterop.realm_sync_session_state(nativePointer)
            return SyncSessionImpl.stateFrom(state)
        }

    override val configuration: SyncConfiguration
        // TODO Get the sync config w/o ever throwing
        get() {
            // Currently `realm` is only `null` when a SyncSession is created for use inside an
            // ErrorHandler, and we expect this to be the only place, so it is safe to spell this
            // out in the error message.
            if (realm == null) {
                throw IllegalStateException("The configuration is not available when inside a `SyncSession.ErrorHandler`.")
            }

            return realm.configuration as SyncConfiguration
        }

    override val user: User
        get() = configuration.user

    override fun pause() {
        RealmInterop.realm_sync_session_pause(nativePointer)
    }

    override fun resume() {
        RealmInterop.realm_sync_session_resume(nativePointer)
    }

    /**
     * Simulates a sync error. Internal visibility only for testing.
     */
    internal fun simulateError(
        errorCode: ProtocolClientErrorCode,
        category: SyncErrorCodeCategory,
        message: String = "Simulate Client Reset"
    ) {
        RealmInterop.realm_sync_session_handle_error_for_testing(
            nativePointer,
            errorCode,
            category,
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
        // Currently `realm` is only `null` when a SyncSession is created for use inside an
        // ErrorHandler, and we expect this to be the only place, so it is safe to spell this
        // out in the error message.
        if (realm == null) {
            throw IllegalStateException(
                """
                Uploading and downloading changes is not allowed when inside 
                a `SyncSession.ErrorHandler`.
                """.trimIndent()
            )
        }
        Validation.require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                withContext(realm.notificationDispatcherHolder.dispatcher) {
                    val callback = object : SyncSessionTransferCompletionCallback {
                        override fun invoke(error: SyncErrorCode?) {
                            if (error != null) {
                                channel.trySend(convertSyncErrorCode(error))
                            } else {
                                channel.trySend(true)
                            }
                        }
                    }.freeze()
                    when (direction) {
                        TransferDirection.UPLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_download_completion(
                                nativePointer,
                                callback
                            )
                        }
                        TransferDirection.DOWNLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_upload_completion(
                                nativePointer,
                                callback
                            )
                        }
                    }
                    channel.receive()
                }
            }
            // We need to refresh the public Realm when downloading to make the changes visible
            // to users immediately.
            // We need to refresh the public Realm when uploading in order to support functionality
            // like `Realm.writeCopyTo()` which require that all changes are uploaded.
            realm.refresh()
            when (result) {
                is Boolean -> return result
                is Throwable -> throw result
                else -> throw IllegalStateException("Unexpected value: $result")
            }
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            return false
        } finally {
            channel.close()
        }
    }

    internal companion object {
        internal fun stateFrom(coreState: CoreSyncSessionState): SyncSession.State {
            return when (coreState) {
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_DYING -> SyncSession.State.DYING
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_ACTIVE -> SyncSession.State.ACTIVE
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_INACTIVE -> SyncSession.State.INACTIVE
                CoreSyncSessionState.RLM_SYNC_SESSION_STATE_WAITING_FOR_ACCESS_TOKEN -> SyncSession.State.WAITING_FOR_ACCESS_TOKEN
                else -> throw IllegalStateException("Unsupported state: $coreState")
            }
        }
    }
}
