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

/**
 * Enum describing the states of the underlying connection used by a [SyncSession].
 */
enum class ConnectionState(val value: Int) {
    /**
     * No connection to the server exists. No data is being transferred even if the session
     * is [SyncSession.State.ACTIVE]. If the connection entered this state due to an error, this
     * error will be reported to the [SyncSession.ErrorHandler].
     */
    DISCONNECTED(0 /*io.realm.mongodb.sync.SyncSession.CONNECTION_VALUE_DISCONNECTED*/),

    /**
     * A connection is currently in progress of being established. If successful the next
     * state is [.CONNECTED]. If the connection fails it will be [.DISCONNECTED].
     */
    CONNECTING(1 /*io.realm.mongodb.sync.SyncSession.CONNECTION_VALUE_CONNECTING*/),

    /**
     * A connection was successfully established to the server. If the SyncSession is [SyncSession.State.ACTIVE]
     * data will now be transferred between the device and the server.
     */
    CONNECTED(2 /*io.realm.mongodb.sync.SyncSession.CONNECTION_VALUE_CONNECTED*/);

    companion object {
        internal fun fromNativeValue(value: Long): ConnectionState {
            val stateCodes = values()
            for (state in stateCodes) {
                if (state.value.toLong() == value) {
                    return state
                }
            }
            throw IllegalArgumentException("Unknown connection state code: $value")
        }
    }
}