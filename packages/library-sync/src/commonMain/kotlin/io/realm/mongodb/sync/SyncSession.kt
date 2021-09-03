/*
 * Copyright 2020 Realm Inc.
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
import io.realm.mongodb.User

/**
 * A session controls how data is synchronized between a single Realm on the device and the server
 * Realm on the Realm Object Server.
 *
 *
 * A Session is created by opening a Realm instance using a [SyncConfiguration]. Once a session has been created,
 * it will continue to exist until the app is closed or all threads using this [SyncConfiguration] closes their respective [Realm]s.
 *
 *
 * A session is controlled by Realm, but can provide additional information in case of errors.
 * These errors are passed along in the [ErrorHandler].
 *
 *
 * When creating a session, Realm will establish a connection to the server. This connection is
 * controlled by Realm and might be shared between multiple sessions. It is possible to get insight
 * into the connection using [.addConnectionChangeListener] and [.isConnected].
 *
 *
 * The session itself has a different lifecycle than the underlying connection. The state of the session
 * can be found using [.getState].
 *
 *
 * The [SyncSession] object is thread safe.
 */
interface SyncSession {

    /**
     * Enum describing the states a SyncSession can be in. The initial state is
     * [State.INACTIVE].
     *
     *
     * A Realm will automatically synchronize data with the server if the session is either [State.ACTIVE]
     * or [State.DYING] and [.isConnected] returns `true`.
     */
    enum class State(val value: Byte) {
        /**
         * This is the initial state. The session is closed. No data is being synchronized. The session
         * will automatically transition to [.ACTIVE] when a Realm is opened.
         */
        INACTIVE(0 /*STATE_VALUE_INACTIVE*/),

        /**
         * The Realm is open and data will be synchronized between the device and the server
         * if the underlying connection is [ConnectionState.CONNECTED].
         *
         *
         * The session will remain in this state until the Realm
         * is closed. In which case it will become [.DYING].
         */
        ACTIVE(1 /*STATE_VALUE_ACTIVE*/),

        /**
         * The Realm was closed, but still contains data that needs to be synchronized to the server.
         * The session will attempt to upload all local data before going [.INACTIVE].
         */
        DYING(2 /*STATE_VALUE_DYING*/),

        /**
         * The user is attempting to synchronize data but needs a valid access token to do so. Realm
         * will either use a cached token or automatically try to acquire one based on the current
         * users login. This requires a network connection.
         *
         *
         * Data cannot be synchronized in this state.
         *
         *
         * Once a valid token is acquired, the session will transition to [.ACTIVE].
         */
        WAITING_FOR_ACCESS_TOKEN(3 /*STATE_VALUE_WAITING_FOR_ACCESS_TOKEN*/);

        companion object {
            internal fun fromNativeValue(value: Long): State {
                val stateCodes = values()
                for (state in stateCodes) {
                    if (state.value.toLong() == value) {
                        return state
                    }
                }
                throw IllegalArgumentException("Unknown session state code: $value")
            }
        }
    }

    /**
     * Returns the [SyncConfiguration] that is responsible for controlling the session.
     *
     * @return SyncConfiguration that defines and controls this session.
     */
    val configuration: SyncConfiguration

    /**
     * Returns the [User] defined by the [SyncConfiguration] that is used to connect to
     * MongoDB Realm.
     *
     * @return [User] used to authenticate the session on MongoDB Realm.
     */
    val user: User

    /**
     * Returns the [URI] describing the remote Realm which this session connects to and synchronizes changes with.
     *
     * @return [URI] describing the remote Realm.
     */
    val serverUrl: URI

    /**
     * Get the current session's state, as defined in [State].
     *
     * Note that the state may change after this method returns.
     *
     * @return the state of the session.
     * @see State
     */
    val state: State

    /**
     * Get the current state of the connection used by the session as defined in [ConnectionState].
     *
     * @return the state of connection used by the session.
     * @see ConnectionState
     */
    val connectionState: ConnectionState

    /**
     * Checks if the session is connected to the server and can synchronize data.
     *
     * This is a best guess effort. To conserve battery the underlying implementation uses heartbeats
     * to  detect if the connection is still available. So if no data is actively being synced
     * and some time has elapsed since the last heartbeat, the connection could have been dropped but
     * this method will still return `true`.
     *
     * @return `true` if the session is connected and ready to synchronize data, `false`
     * if not or if it is in the process of connecting.
     */
    val isConnected: Boolean

    /**
     * Adds a progress listener tracking changes that need to be downloaded from the Realm Object
     * Server.
     *
     * The [ProgressListener] will be triggered immediately when registered, and periodically
     * afterwards.
     *
     * @param mode type of mode used. See [ProgressMode] for more information.
     * @param listener the listener to register.
     */
    fun addDownloadProgressListener(mode: ProgressMode, listener: ProgressListener)

    /**
     * Adds a progress listener tracking changes that need to be uploaded from the device to the
     * Realm Object Server.
     *
     *
     * The [ProgressListener] will be triggered immediately when registered, and periodically
     * afterwards.
     *
     * @param mode type of mode used. See [ProgressMode] for more information.
     * @param listener the listener to register.
     */
    fun addUploadProgressListener(mode: ProgressMode, listener: ProgressListener): Cancellable

    /**
     * Adds a listener tracking changes to the connection backing this session. See [ConnectionState]
     * for further details.
     *
     * @param listener the listener to register.
     * @throws IllegalArgumentException if the listener is `null`.
     * @see ConnectionState
     */
    fun addConnectionChangeListener(listener: ConnectionListener): Cancellable

    /**
     * Calling this method will block until all known remote changes have been downloaded and applied to the Realm
     * or the specified timeout is hit. This will involve network access, so calling this method should only be done
     * from a non-UI thread.
     *
     *
     * This method cannot be called before the Realm has been opened.
     *
     * @throws IllegalStateException if called on the Android main thread.
     * @throws InterruptedException if the download took longer than the specified timeout or the thread was interrupted while downloading was in progress.
     * The download will continue in the background even after this exception is thrown.
     * @throws IllegalArgumentException if `timeout` is less than or equal to `0` or `unit` is `null`.
     * @return `true` if the data was downloaded before the timeout. `false` if the operation timed out or otherwise failed.
     */
    fun downloadAllServerChanges(timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.SECONDS): Boolean

    /**
     * Calling this method will block until all known local changes have been uploaded to the server or the specified
     * timeout is hit. This will involve network access, so calling this method should only be done from a non-UI
     * thread.
     *
     *
     * This method cannot be called before the Realm has been opened.
     *
     * @throws IllegalStateException if called on the Android main thread.
     * @throws InterruptedException if the upload took longer than the specified timeout or the thread was interrupted while uploading was in progress.
     * The upload will continue in the background even after this exception is thrown.
     * @throws IllegalArgumentException if `timeout` is less than or equal to `0` or `unit` is `null`.
     * @return `true` if the data was uploaded before the timeout. `false` if the operation timed out or otherwise failed.
     */
    fun uploadAllLocalChanges(timeout: Long = Long.MAX_VALUE, unit: TimeUnit = TimeUnit.SECONDS): Boolean

    /**
     * Attempts to start the session and enable synchronization with the Realm Object Server.
     *
     * This happens automatically when opening the Realm instance, so doing it manually should only
     * be needed if the session was stopped using [.stop].
     *
     * If the session was already started, calling this method will do nothing.
     *
     * A session is considered started if [.getState] returns [State.ACTIVE].
     * If the session is [State.DYING], the session
     * will be moved back to [State.ACTIVE].
     *
     * @see .getState
     * @see .stop
     */
    fun start()

    /**
     * Stops any synchronization with the Realm Object Server until the Realm is re-opened again
     * after fully closing it.
     *
     * Synchronization can be re-enabled by calling [.start] again.
     *
     * If the session is already stopped, calling this method will do nothing.
     */
    fun stop()

    /**
     * Interface used to report any session errors.
     *
     * @see SyncConfiguration.Builder.errorHandler
     */
    interface ErrorHandler {
        /**
         * Callback for errors on a session object. It is not allowed to throw an exception inside an error handler.
         * If the operations in an error handler can throw, it is safer to catch any exception in the error handler.
         * When an exception is thrown in the error handler, the occurrence will be logged and the exception
         * will be ignored.
         *
         * @param session [SyncSession] this error happened on.
         * @param error type of error.
         */
        fun onError(session: SyncSession?, error: AppException?)
    }

    /**
     * Callback for the specific error event known as a Client Reset, determined by the error code
     * [ErrorCode.CLIENT_RESET].
     *
     *
     * A synced Realm may need to be reset because the MongoDB Realm Server encountered an error and had
     * to be restored from a backup or because it has been too long since the client connected to the
     * server so the server has rotated the logs.
     *
     *
     * The Client Reset thus occurs because the server does not have the full information required to
     * bring the Client fully up to date.
     *
     *
     * The reset process is as follows: the local copy of the Realm is copied into a recovery directory
     * for safekeeping, and then deleted from the original location. The next time the Realm for that
     * URL is opened, the Realm will automatically be re-downloaded from MongoDB Realm, and
     * can be used as normal.
     *
     *
     * Data written to the Realm after the local copy of the Realm diverged from the backup remote copy
     * will be present in the local recovery copy of the Realm file. The re-downloaded Realm will
     * initially contain only the data present at the time the Realm was backed up on the server.
     *
     *
     * The client reset process can be initiated in one of two ways:
     *
     *  1.
     * Run [ClientResetRequiredError.executeClientReset] manually. All Realm instances must be
     * closed before this method is called.
     *
     *  1.
     * If Client Reset isn't executed manually, it will automatically be carried out the next time all
     * Realm instances have been closed and re-opened. This will most likely be
     * when the app is restarted.
     *
     *
     *
     * **WARNING:**
     * Any writes to the Realm file between this callback and Client Reset has been executed, will not be
     * synchronized to MongoDB Realm. Those changes will only be present in the backed up file. It is therefore
     * recommended to close all open Realm instances as soon as possible.
     */
    interface ClientResetHandler {
        /**
         * Callback that indicates a Client Reset has happened. This should be handled as quickly as
         * possible as any further changes to the Realm will not be synchronized with the server and
         * must be moved manually from the backup Realm to the new one.
         *
         * @param session [SyncSession] this error happened on.
         * @param error [ClientResetRequiredError] the specific Client Reset error.
         */
        fun onClientReset(session: SyncSession?, error: ClientResetRequiredError?)
    }

}