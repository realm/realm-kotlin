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

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Builder for configuring various timeouts related to the sync connection to the server.
 *
 * @see [io.realm.kotlin.mongodb.AppConfiguration.Builder.syncTimeouts]
 */
public class SyncTimeoutOptionsBuilder {

    /**
     * The maximum time to allow for a connection to become fully established. This includes
     * the time to resolve the network address, the TCP connect operation, the SSL
     * handshake, and the WebSocket handshake.
     *
     * Only values >= 1 second is allowed. Default is 2 minutes.
     *
     * @throws IllegalArgumentException if the duration is outside the allowed range.
     */
    public var connectTimeout: Duration = 2.minutes
        set(value) {
            if (value < 1.seconds) {
                throw IllegalArgumentException("connectTimeout only support durations >= 1 second. This was: $value")
            }
            field = value
        }

    /**
     * If session multiplexing is enabled, how long to keep connections open while there are
     * no active session.
     *
     * Only durations > 0 seconds are allowed. Default is 30 seconds.
     *
     * @throws IllegalArgumentException if the duration is outside the allowed range.
     * @see io.realm.kotlin.mongodb.AppConfiguration.Builder.enableSessionMultiplexing
     */
    public var connectionLingerTime: Duration = 30.seconds
        set(value) {
            if (value <= 0.milliseconds) {
                throw IllegalArgumentException("connectionLingerTime must be a positive duration > 0. This was: $value")
            }
            field = value
        }

    /**
     * How long to wait between each ping message sent to the server. The client periodically
     * sends ping messages to the server to check if the connection is still alive. Shorter
     * periods make connection state change notifications more responsive at the cost of
     * battery life (as the antenna will have to wake up more often).
     *
     * Only durations > 5 seconds are allowed. Default is 1 minute.
     *
     * @throws IllegalArgumentException if the duration is outside the allowed range.
     */
    public var pingKeepalivePeriod: Duration = 1.minutes
        set(value) {
            if (value <= 5.seconds) {
                throw IllegalArgumentException("pingKeepalivePeriod must be a positive duration > 5 seconds. This was: $value")
            }
            field = value
        }

    /**
     * How long to wait for the server to respond to a ping message. Shorter values make
     * connection state change notifications more responsive, but increase the chance of
     * spurious disconnections.
     *
     * Only durations > 5 seconds are allowed. Default is 2 minutes.
     *
     * @throws IllegalArgumentException if the duration is outside the allowed range.
     */
    public var pongKeepalivePeriod: Duration = 2.minutes
        set(value) {
            if (value <= 5.seconds) {
                throw IllegalArgumentException("pongKeepalivePeriod must be a positive duration > 5 seconds. This was: $value")
            }
            field = value
        }

    /**
     * When a client first connects to the server, it downloads all data from the server
     * before it begins to upload local changes. This typically reduces the total amount
     * of merging needed and  gets the local client into a useful state faster. If a
     * disconnect and reconnect happens within the time span of the fast reconnect limit,
     * this is skipped and the session behaves as if it were continuously
     * connected.
     *
     * Only durations > 1 second are allowed. Default is 1 minute.
     *
     * @throws IllegalArgumentException if the duration is outside the allowed range.
     */
    public var fastReconnectLimit: Duration = 1.minutes
        set(value) {
            if (value <= 1.seconds) {
                throw IllegalArgumentException("fastReconnectLimit must be a positive duration > 1 second. This was: $value")
            }
            field = value
        }

    /**
     * Construct the final [SyncTimeoutOptions] object.
     */
    internal fun build() = SyncTimeoutOptions(
        connectTimeout = this.connectTimeout,
        connectionLingerTime = this.connectionLingerTime,
        pingKeepalivePeriod = this.pingKeepalivePeriod,
        pongKeepalivePeriod = this.pongKeepalivePeriod,
        fastReconnectLimit = this.fastReconnectLimit
    )
}
