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

package io.realm.mongodb.sync

import io.realm.Realm
import io.realm.mongodb.exceptions.SyncException
import kotlin.time.Duration

/**
 * A session controls how data is synchronized between a single Realm on the device and MongoDB on
 * the server.
 *
 * A `SyncSession` is created by opening a Realm instance using a [SyncConfiguration]. Once a
 * session has been created, it will continue to exist until the app is closed or the [Realm] is
 * closed.
 *
 * A session is controlled by Realm, but can provide additional information in case of errors.
 * These errors are passed along in the [ErrorHandler].
 *
 * When creating a session, Realm will establish a connection to the server. This connection is
 * controlled by Realm and might be shared between multiple sessions.
 *
 * The session itself has a different lifecycle than the underlying connection.
 *
 * The [SyncSession] object is thread safe.
 */
public interface SyncSession {

    /**
     * Calling this method will block until all known remote changes have been downloaded and
     * applied to the Realm or the specified timeout is hit. This will involve network access, so
     * calling this method should only be done from a non-UI thread.
     *
     * @param timeout Maximum amount of time before this method should return.
     * @return `true` if the data was downloaded. `false` if the download timed out before it
     * could complete. The download will continue in the background, even after returning `false`.
     * @throws IllegalArgumentException if `timeout` is <= 0.
     * @throws IllegalStateException if called from inside a [SyncSession.ErrorHandler].
     * @throws SyncException if a problem was encountered with the connection during the download.
     */
    public suspend fun downloadAllServerChanges(timeout: Duration = Duration.INFINITE): Boolean

    /**
     * Calling this method will block until all known local changes have been uploaded to the server
     * or the specified timeout is hit. This will involve network access, so calling this method
     * should only be done from a non-UI thread.
     *
     * @param timeout Maximum amount of time before this method should return.
     * @return `true` if the data was uploaded. `false` if the upload timed out before it
     * could complete. The upload will continue in the background, even after returning `false`.
     * @throws IllegalArgumentException if `timeout` is <= 0.
     * @throws IllegalStateException if called from inside a [SyncSession.ErrorHandler].
     * @throws SyncException if a problem was encountered with the connection during the upload.
     */
    public suspend fun uploadAllLocalChanges(timeout: Duration = Duration.INFINITE): Boolean

    /**
     * The current session state. See [SyncSessionState] for more details about each state.
     */
    public val state: SyncSessionState

    /**
     * Pauses any synchronization with the Realm Object Server until the Realm is re-opened again
     * after fully closing it.
     * <p>
     * Synchronization can be re-activated by calling [resume] again.
     * <p>
     * If the session is already [SyncSessionState.INACTIVE], calling this method will do nothing.
     */
    public fun pause()

    /**
     * Attempts to resume the session and activate synchronization with the Realm Object Server.
     * <p>
     * This happens automatically when opening the Realm instance, so doing it manually should only
     * be needed if the session was paused using [pause].
     * <p>
     * If the session was already [SyncSessionState.ACTIVE], calling this method will do nothing.
     * <p>
     * If the session state is [SyncSessionState.DYING], the session will be moved back to
     * [SyncSessionState.ACTIVE].
     */
    public fun resume()

    /**
     * Interface used to report any session errors.
     *
     * @see SyncConfiguration.Builder.errorHandler
     */
    public fun interface ErrorHandler {
        /**
         * Callback for errors on a session object. It is not recommended to throw an exception
         * inside an error handler, as the exception will be caught, logged, and ignored by Realm.
         * Instead, it is better to manually catch all exceptions and manually handle these
         * exceptions.
         *
         * @param session the [SyncSession] in which this error happened.
         * @param error the [SyncException] being reported by the server.
         */
        public fun onError(session: SyncSession, error: SyncException)
    }
}

/**
 * The possible states for [SyncSession] to be.
 */
public enum class SyncSessionState {
    ACTIVE,
    DYING,
    INACTIVE,
    WAITING_FOR_ACCESS_TOKEN
}
