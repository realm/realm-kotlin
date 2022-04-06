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
import io.realm.internal.RealmImpl
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSyncSessionPointer
import io.realm.internal.interop.SyncSessionTransferCompletionCallback
import io.realm.internal.platform.freeze
import io.realm.mongodb.SyncErrorCode
import io.realm.mongodb.SyncException
import io.realm.mongodb.SyncSession
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
    // `downloadAllServerChanges` inside the erorr handler, we are just going to disallow it by
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
        if (realm == null) {
            throw IllegalStateException("Uploading and downloading changes is not allowed when inside a `SyncSession.ErrorHandler`.")
        }
        require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }

        // Channel to work around not being able to use `suspendCoroutine` to wrap the callback, as
        // that results in the `Continuation` being frozen, which breaks it.
        val channel = Channel<Any>(1)
        try {
            val result: Any = withTimeout(timeout) {
                withContext(realm.configuration.notificationDispatcher) {
                    val callback = object : SyncSessionTransferCompletionCallback {
                        override fun invoke(error: SyncErrorCode?) {
                            if (error != null) {
                                channel.trySend(SyncException(error.toString()))
                            } else {
                                channel.trySend(true)
                            }
                        }
                    }.freeze()
                    when (direction) {
                        TransferDirection.UPLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_download_completion(nativePointer, callback)
                        }
                        TransferDirection.DOWNLOAD -> {
                            RealmInterop.realm_sync_session_wait_for_upload_completion(nativePointer, callback)
                        }
                    }
                    channel.receive()
                }
            }
            if (direction == TransferDirection.DOWNLOAD) {
                realm.refresh()
            }
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
}
