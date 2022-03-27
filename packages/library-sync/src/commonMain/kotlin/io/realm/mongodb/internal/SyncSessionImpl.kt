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
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.SyncSessionTransferCompletionCallback
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.mongodb.SyncSession
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.Duration
import io.realm.mongodb.SyncErrorCode
import io.realm.mongodb.AppException

internal open class SyncSessionImpl(
    internal val nativePointer: NativePointer
) : SyncSession {

    private enum class TransferDirection {
        UPLOAD, DOWNLOAD
    }

    // Context for handling `uploadAllLocalChanges()` or `downloadAllServerChanges()`. A single
    // threaded dispatcher is required as it also works as a mutex, only allowing one call to be
    // active at a time.
    // FIXME This was needed in Java, but not sure we have the same constraints in Kotlin. But what context should we use here?
    private val waitForChangesContext: CoroutineContext = singleThreadDispatcher("SyncSession-WaitForChanges")

    override suspend fun downloadAllServerChanges(timeout: Duration) {
        waitForChanges(TransferDirection.DOWNLOAD, timeout)
    }

    override suspend fun uploadAllLocalChanges(timeout: Duration) {
        waitForChanges(TransferDirection.UPLOAD, timeout)
    }

    /**
     * Wrap Core callbacks that will not be invoked until data has been either fully uploaded
     * or downloaded.
     *
     * FIXME What should happen if we call this inside a write transaction?
     *
     * When this method returns. The user facing Realm has been updated to the latest state.
     *
     * @param direction whether data is being uploaded or downloaded.
     * @param timeout timeout parameter.
     * @return `true` if the job completed before the timeout was hit, `false` otherwise.
     */
    private suspend fun waitForChanges(direction: TransferDirection, timeout: Duration): Boolean {
        require(timeout.isPositive()) {
            "'timeout' must be > 0. It was: $timeout"
        }
        try {
            val result: Boolean = withTimeout(timeout) {
                withContext(waitForChangesContext) {
                    val result: Boolean = suspendCoroutine<Boolean> { cont: Continuation<Boolean> ->
                        val callback = object: SyncSessionTransferCompletionCallback {
                            override fun invoke(error: SyncErrorCode?) {
                                if (error != null) {
                                    cont.resumeWithException(AppException(error.toString()))
                                } else {
                                    cont.resumeWith(Result.success(true))
                                }
                            }
                        }
                        when(direction) {
                            TransferDirection.UPLOAD -> {
                                RealmInterop.realm_sync_session_wait_for_download_completion(nativePointer, callback)
                            }
                            TransferDirection.DOWNLOAD -> {
                                RealmInterop.realm_sync_session_wait_for_upload_completion(nativePointer, callback)
                            }
                        }
                    }
                    result
                }
            }
            // FIXME Ideally we should update the Realm as well here, but how to do that?
            return result
        } catch (ex: TimeoutCancellationException) {
            // Don't throw if timeout is hit, instead just return false per the API contract.
            return false
        }
    }

}
