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
interface SyncSession {

    /**
     * Interface used to report any session errors.
     *
     * @see SyncConfiguration.Builder.errorHandler
     */
    interface ErrorHandler {
        /**
         * Callback for errors on a session object. It is not recommended to throw an exception
         * inside an error handler, as the exception will be caught, logged, and ignored by Realm.
         * Instead, it is better to manually catch all exceptions and manually handle these
         * exceptions.
         *
         * @param session the [SyncSession] in which this error happened.
         * @param error the [SyncException] being reported by the server.
         */
        fun onError(session: SyncSession, error: SyncException)
    }
}
