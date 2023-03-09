/*
 * Copyright 2023 Realm Inc.
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

/**
 * A **connection state** indicates the state of the underlying connection of the [SyncSession].
 */
public enum class ConnectionState {

    /**
     * Indicates that there is no connection to the server. No data is being transferred even if
     * the session is [SyncSession.State.ACTIVE]. If the connection entered this state due to an
     * error, the error is reported in the [SyncConfiguration.errorHandler].
     */
    DISCONNECTED,

    /**
     * Indicates that a connection is currently in progress of being established. If successful the
     * next state is [CONNECTED], otherwise it will be [DISCONNECTED].
     */
    CONNECTING,

    /**
     * Indicates that a connection is successfully established to the server. If the SyncSession is
     * [SyncSession.State.ACTIVE] then data will now be transferred between the device and the
     * server.
     */
    CONNECTED,
}
