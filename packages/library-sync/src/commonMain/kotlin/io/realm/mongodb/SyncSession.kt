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

package io.realm.mongodb

import io.realm.Realm

/**
 * A session controls how data is synchronized between a single Realm on the device and the server
 * Realm on the Realm Object Server.
 *
 * A `SyncSession` is created by opening a Realm instance using a [SyncConfiguration]. Once a
 * session has been created, it will continue to exist until the app is closed or all threads using
 * this [SyncConfiguration] close their respective [Realm]s.
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
interface SyncSession {

    /**
     * Interface used to report any session errors.
     *
     * @see SyncConfiguration.Builder.errorHandler
     */
    interface ErrorHandler {
        /**
         * Callback for errors on a session object. It is not allowed to throw an exception inside
         * an error handler. If the operations in an error handler can throw, it is safer to catch
         * any exception in the error handler. When an exception is thrown in the error handler, the
         * occurrence will be logged and the exception will be ignored.
         *
         * @param session the [SyncSession] in which this error happened.
         * @param error the type of error.
         */
        fun onError(session: SyncSession, error: AppException)
    }
}
