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

interface WebSocketTransport {
    fun post(handlerCallback: RealmWebsocketHandlerCallbackPointer)

    fun createTimer(
        delayInMilliseconds: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer,
    ): CancellableTimer

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

    fun write(
        webSocketClient: WebSocketClient,
        data: ByteArray,
        length: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer
    )

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

    fun close()
}

class CancellableTimer(
    private val job: Job,
    private val cancelCallback: () -> Unit
) {
    fun cancel() {
        job.cancel()
        cancelCallback()
    }
}

interface WebSocketClient {
    fun send(message: ByteArray, handlerCallback: RealmWebsocketHandlerCallbackPointer)
    fun close()
}

interface WebsocketEngine {
    fun shutdown()
    fun <T> getInstance(): T
}

class WebSocketObserver(private val webSocketObserverPointer: RealmWebsocketProviderPointer) {
    fun onConnected(protocol: String) {
        RealmInterop.realm_sync_socket_websocket_connected(webSocketObserverPointer, protocol)
    }

    fun onError() {
        RealmInterop.realm_sync_socket_websocket_error(webSocketObserverPointer)
    }

    fun onNewMessage(data: ByteArray): Boolean {
        return RealmInterop.realm_sync_socket_websocket_message(webSocketObserverPointer, data)
    }

    fun onClose(wasClean: Boolean, errorCode: WebsocketErrorCode, reason: String) {
        RealmInterop.realm_sync_socket_websocket_closed(
            webSocketObserverPointer, wasClean, errorCode, reason
        )
    }
}
