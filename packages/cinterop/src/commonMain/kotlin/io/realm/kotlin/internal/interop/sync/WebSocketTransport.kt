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
package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmWebsocketHandlerCallbackPointer
import io.realm.kotlin.internal.interop.RealmWebsocketProviderPointer
import kotlinx.coroutines.Job

/**
 * Interface to be implemented by the websocket provider. This helps un-bundle the implementation
 * from Core to leverage the platform capabilities (Proxy, firewall, vpn etc.).
 */
interface WebSocketTransport {
    /**
     * Submit a handler function to be executed by the event loop.
     */
    fun post(handlerCallback: RealmWebsocketHandlerCallbackPointer)

    /**
     * Create and register a new timer whose handler function will be posted
     * to the event loop when the provided delay expires.
     * @return [CancellableTimer] to be called if the timer is to be cancelled before the delay.
     */
    fun createTimer(
        delayInMilliseconds: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer,
    ): CancellableTimer

    /**
     * Create a new websocket pointed to the server indicated by endpoint and
     * connect to the server. Any events that occur during the execution of the
     * websocket will call directly to the handlers provided by the observer (new messages, error, close events)
     *
     * @return [WebSocketClient] instance to be used by Core to send data, and signal a close session.
     */
    @Suppress("LongParameterList")
    fun connect(
        observer: WebSocketObserver,
        path: String,
        address: String,
        port: Long,
        isSsl: Boolean,
        numProtocols: Long,
        supportedSyncProtocols: String
    ): WebSocketClient

    /**
     * Writes to the previously created websocket in [connect] the binary data. The provided [handlerCallback] needs
     * to run in the event loop after a successful write or in case of an error.
     */
    fun write(
        webSocketClient: WebSocketClient,
        data: ByteArray,
        length: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer
    )

    /**
     * This helper function run the provided function pointer. It needs to be called within the same event loop context (thread)
     * as the rest of the other functions.
     */
    fun runCallback(
        handlerCallback: RealmWebsocketHandlerCallbackPointer,
        cancelled: Boolean = false,
        status: WebsocketCallbackResult = WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS,
        reason: String = ""
    ) {
        RealmInterop.realm_sync_socket_callback_complete(
            handlerCallback, cancelled, status, reason
        )
    }

    /**
     * Core signal the transport, all websockets previously created with [connect] would have been closed at this point
     * this is useful to do any resource cleanup like shutting down the engine or closing coroutine dispatcher.
     */
    fun close()
}

/**
 * Cancel a previously scheduled timer created via [WebSocketTransport.createTimer].
 */
class CancellableTimer(
    private val job: Job,
    private val cancelCallback: () -> Unit
) {
    fun cancel() {
        job.cancel()
        cancelCallback()
    }
}

/**
 * Define an interface to interact with the websocket created via [WebSocketTransport.connect].
 * This will be called from Core.
 */
interface WebSocketClient {
    /**
     * Send a binary Frame to the remote peer.
     */
    fun send(message: ByteArray, handlerCallback: RealmWebsocketHandlerCallbackPointer)

    /**
     * Close the websocket.
     */
    fun close()
}

/**
 * Defines an abstraction of the underlying Http engine used to create the websocket.
 * This abstraction is needed in order to deterministically create and shutdown the engine at the transport level.
 * All websocket within the same App share the same transport and by definition the same engine.
 */
interface WebsocketEngine {
    fun shutdown()
    fun <T> getInstance(): T
}

/**
 * Wrapper around Core callback pointer (observer). This will delegate calls for all incoming messages from the remote peer.
 */
class WebSocketObserver(private val webSocketObserverPointer: RealmWebsocketProviderPointer) {
    /**
     * Communicate the negotiated Sync protocol.
     */
    fun onConnected(protocol: String) {
        RealmInterop.realm_sync_socket_websocket_connected(webSocketObserverPointer, protocol)
    }

    /**
     * Notify an error.
     */
    fun onError() {
        RealmInterop.realm_sync_socket_websocket_error(webSocketObserverPointer)
    }

    /**
     * Forward received message to Core.
     */
    fun onNewMessage(data: ByteArray): Boolean {
        return RealmInterop.realm_sync_socket_websocket_message(webSocketObserverPointer, data)
    }

    /**
     * Notify closure message.
     */
    fun onClose(wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String) {
        RealmInterop.realm_sync_socket_websocket_closed(
            webSocketObserverPointer, wasClean, errorCode, reason
        )
    }
}
