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

/**
 * The configured timeouts for various aspects of the sync connection between synchronized realms
 * and App Services.
 *
 * @see [io.realm.kotlin.mongodb.AppConfiguration.Builder.syncTimeouts]
 */
public data class SyncTimeoutOptions(

    /**
     * The maximum time to allow for a connection to become fully established. This includes
     * the time to resolve the network address, the TCP connect operation, the SSL
     * handshake, and the WebSocket handshake.
     */
    val connectTimeout: Duration,

    /**
     * If session multiplexing is enabled, how long to keep connections open while there are
     * no active session.
     */
    val connectionLingerTime: Duration,

    /**
     * How long to wait between each ping message sent to the server. The client periodically
     * sends ping messages to the server to check if the connection is still alive. Shorter
     * periods make connection state change notifications more responsive at the cost of
     * more trafic.
     */
    val pingKeepalivePeriod: Duration,

    /**
     * How long to wait for the server to respond to a ping message. Shorter values make
     * connection state change notifications more responsive, but increase the chance of
     * spurious disconnections.
     */
    val pongKeepalivePeriod: Duration,

    /**
     * When a client first connects to the server, it downloads all data from the server
     * before it begins to upload local changes. This typically reduces the total amount
     * of merging needed and gets the local client into a useful state faster. If a
     * disconnect and reconnect happens within the time span of the fast reconnect limit,
     * this is skipped and the session behaves as if it were continuously
     * connected.
     */
    val fastReconnectLimit: Duration
)
