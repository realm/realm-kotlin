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

package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.Realm
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.exceptions.SyncException
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
     * The current session state. See [State] for more details about each state.
     */
    public val state: State

    /**
     * The [SyncConfiguration] responsible for controlling the session.
     *
     * @throws IllegalStateException if accessed from inside a [SyncSession.ErrorHandler] due to session errors.
     */
    public val configuration: SyncConfiguration

    /**
     * The [User] used to authenticate the session on Atlas App Services.
     */
    public val user: User

    /**
     * Pauses synchronization with Atlas until the Realm is closed and re-opened again.
     *
     * Synchronization can also be re-activated by calling [resume].
     *
     * If the session is already [State.INACTIVE], calling this method will do nothing.
     */
    public fun pause()

    /**
     * Attempts to resume the session and activate synchronization with Atlas.
     *
     * This happens automatically when opening the Realm, so doing it manually should only
     * be needed if the session was paused using [pause].
     *
     * If the session was already [State.ACTIVE], calling this method will do nothing.
     *
     * If the session state is [State.DYING], the session will be moved back to [State.ACTIVE].
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

    /**
     * The possible states for [SyncSession] to be.
     *
     * A Realm will automatically synchronize data with the server if the session is either [ACTIVE]
     * or [DYING] and is connected.
     */
    public enum class State {
        /**
         * This is the initial state. The session is closed. No data is being synchronized. The session
         * will automatically transition to [ACTIVE] when a Realm is opened.
         */
        INACTIVE,

        /**
         * The Realm is open and data will be synchronized between the device and the server
         * if the underlying connection is connected.
         *
         * The session will remain in this state until the Realm
         * is closed. In which case it will become [DYING].
         */
        ACTIVE,

        /**
         * The Realm was closed, but still contains data that needs to be synchronized to the server.
         * The session will attempt to upload all local data before going [INACTIVE].
         */
        DYING,

        /**
         * The user is attempting to synchronize data but needs a valid access token to do so. Realm
         * will either use a cached token or automatically try to acquire one based on the current
         * users login. This requires a network connection.
         *
         * Data cannot be synchronized in this state.
         *
         * Once a valid token is acquired, the session will transition to [ACTIVE].
         */
        WAITING_FOR_ACCESS_TOKEN
    }
}
