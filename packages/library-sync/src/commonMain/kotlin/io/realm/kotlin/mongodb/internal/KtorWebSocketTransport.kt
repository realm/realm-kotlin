package io.realm.kotlin.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.ClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readReason
import io.ktor.websocket.readText
import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.internal.interop.RealmWebsocketHandlerCallbackPointer
import io.realm.kotlin.internal.interop.sync.CancellableTimer
import io.realm.kotlin.internal.interop.sync.WebSocketClient
import io.realm.kotlin.internal.interop.sync.WebSocketObserver
import io.realm.kotlin.internal.interop.sync.WebSocketTransport
import io.realm.kotlin.internal.interop.sync.WebsocketErrorCode
import io.realm.kotlin.internal.util.DispatcherHolder
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

public class KtorWebSocketTransport(
    timeoutMs: Long,
    private val dispatcherHolder: DispatcherHolder
) : WebSocketTransport {

    private val logger = ContextLogger("Websocket")
    private val client: HttpClient by lazy { createWebSocketClient(timeoutMs) }
    private val transportJob: CompletableJob by lazy { Job() }
    private val scope: CoroutineScope by lazy { CoroutineScope(dispatcherHolder.dispatcher + transportJob) }

    override fun post(handlerCallback: RealmWebsocketHandlerCallbackPointer) {
        scope.launch {
            (this as Job).invokeOnCompletion { completionHandler: Throwable? ->
                // Only run the callback if it was not cancelled in the meantime
                when (completionHandler) {
                    null -> runCallback(handlerCallback)
                    else -> runCallback(
                        handlerCallback, cancelled = true
                    )
                }
            }
        }
    }

    override fun createTimer(
        delayInMilliseconds: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer
    ): CancellableTimer {
        val atomicCallback: AtomicRef<RealmWebsocketHandlerCallbackPointer?> = atomic(handlerCallback)
        return CancellableTimer(
            scope.launch {
                delay(delayInMilliseconds)
                atomicCallback.getAndSet(null)?.run {
                    runCallback(this)
                }
            }
        ) { // Cancel lambda
            scope.launch {
                atomicCallback.getAndSet(null)?.run {
                    runCallback(this, cancelled = true)
                }
            }
        }
    }

    override fun connect(
        observer: WebSocketObserver,
        path: String,
        address: String,
        port: Long,
        isSsl: Boolean,
        numProtocols: Long,
        supportedProtocols: String
    ): WebSocketClient {

        return object : WebSocketClient {
            private val websocketJob: CompletableJob by lazy { Job() }
            private val websocketExceptionHandler: CoroutineExceptionHandler by lazy {
                CoroutineExceptionHandler { _, exception: Throwable ->
                    logger.error(exception)
                    closeWebsocket()
                }
            }
            private val scope: CoroutineScope by lazy { CoroutineScope(dispatcherHolder.dispatcher + websocketJob + websocketExceptionHandler) }
            private val writeChannel =
                Channel<Pair<ByteArray, RealmWebsocketHandlerCallbackPointer>>(capacity = UNLIMITED)

            private val binaryBuffer = FrameBuffer {
                observer.onNewMessage(it)
            }
            private lateinit var session: ClientWebSocketSession

            init {
                openConnection()
            }

            @Suppress("LongMethod")
            private fun openConnection() {
                scope.launch {
                    client.webSocket(
                        method = HttpMethod.Get,
                        host = address,
                        port = port.toInt(),
                        path = path,
                        request = {
                            url.protocol = if (isSsl) URLProtocol.WSS else URLProtocol.WS
                            header(HttpHeaders.SecWebSocketProtocol, supportedProtocols)
                        },
                    ) {
                        session = this
                        // it's unlikely to get a WebSocketSession without a successful protocol switch
                        // but we're double checking the status here
                        if (call.response.status != HttpStatusCode.SwitchingProtocols) {
                            observer.onError()
                            observer.onClose(
                                wasClean = false,
                                errorCode = WebsocketErrorCode.RLM_ERR_WEBSOCKET_CONNECTION_FAILED,
                                reason = "Websocket server responded with status code ${call.response.status} instead of ${HttpStatusCode.SwitchingProtocols}"
                            )
                        } else {
                            when (
                                val selectedProtocol =
                                    call.response.headers[HttpHeaders.SecWebSocketProtocol]
                            ) {
                                null -> {
                                    observer.onError()
                                    observer.onClose(
                                        false,
                                        WebsocketErrorCode.RLM_ERR_WEBSOCKET_PROTOCOLERROR,
                                        "${HttpHeaders.SecWebSocketProtocol} header not returned. Sync server didn't return supported protocol" + ". Supported protocols are = $supportedProtocols"
                                    )
                                    close(
                                        CloseReason(
                                            CloseReason.Codes.PROTOCOL_ERROR,
                                            "Server didn't select a supported protocol. Supported protocols are = $supportedProtocols"
                                        )
                                    ) // Sends a [Frame.Close].
                                }

                                else -> {
                                    scope.launch {
                                        observer.onConnected(selectedProtocol)
                                    }
                                }
                            }

                            // Writing messages to WebSocket
                            scope.launch {
                                writeChannel.consumeEach {
                                    // There's no fragmentation needed when sending frames from client
                                    // so 'fin' should always be `true`
                                    outgoing.send(Frame.Binary(true, it.first))
                                    runCallback(it.second)
                                }
                            }

                            // Reading messages from WebSocket
                            scope.launch {
                                incoming.consumeEach {
                                    when (val frame = it) {
                                        is Frame.Binary -> {
                                            val shouldCloseSocket =
                                                binaryBuffer.appendAndSend(frame)
                                            if (shouldCloseSocket) {
                                                closeWebsocket()
                                            }
                                        }

                                        is Frame.Close -> {
                                            // It's important to rely properly the error code from the server.
                                            // The server will report auth errors (and a few other error types)
                                            // as websocket application-level errors after establishing the socket, rather than failing at the HTTP layer.
                                            // since the websocket spec does not allow the HTTP status code from the response to be
                                            // passed back to the client from the websocket implementation (example instruct a refresh token
                                            // via a 401 HTTP response is not possible) see https://jira.mongodb.org/browse/BAAS-10531.
                                            // In order to provide a reasonable response that the Sync Client can react upon, the private range of websocket close status codes
                                            // 4000-4999, can be used to return a more specific error.
                                            val errorCode: WebsocketErrorCode =
                                                frame.readReason()?.code?.toInt()
                                                    ?.let { code -> WebsocketErrorCode.of(code) }
                                                    ?: WebsocketErrorCode.RLM_ERR_WEBSOCKET_OK
                                            val reason: String = frame.readReason()?.toString()
                                                ?: "Received Close from Websocket server"

                                            observer.onClose(true, errorCode, reason)
                                        }

                                        is Frame.Text -> {
                                            logger.warn("Received unexpected text WebSocket message ${frame.readText()}")
                                        }

                                        // Raw WebSocket Frames (i.e Frame.Ping & Frame.Pong) are handled automatically with the client
                                        // (Core doesn't care about these in the new API)
                                        else -> {
                                            logger.warn("Received unexpected message from server, Frame type = ${frame.frameType}")
                                        }
                                    }
                                }
                            }
                            websocketJob.join() // otherwise the client will send end the session and send a Close
                        }
                    }
                }
            }

            override fun send(
                message: ByteArray,
                handlerCallback: RealmWebsocketHandlerCallbackPointer
            ) {
                writeChannel.trySend(Pair(message, handlerCallback))
            }

            override fun closeWebsocket() {
                if (::session.isInitialized) {
                    session.cancel() // Terminate the WebSocket session, connect needs to be called again.
                }
                // Collect unprocessed writes and cancel them (mainly to avoid leaking the FunctionHandler).
                while (true) {
                    val result = writeChannel.tryReceive()
                    if (result.isSuccess) {
                        result.getOrNull()?.run {
                            runBlocking(scope.coroutineContext) {
                                runCallback(handlerCallback = second, cancelled = true)
                            }
                        }
                    } else {
                        // No more elements in the channel
                        break
                    }
                }
                writeChannel.close()
                websocketJob.cancel() // Cancel all scheduled jobs.
            }
        }
    }

    override fun write(
        webSocketClient: WebSocketClient,
        data: ByteArray,
        length: Long,
        handlerCallback: RealmWebsocketHandlerCallbackPointer
    ) {
        webSocketClient.send(data, handlerCallback)
    }

    override fun close() {
        transportJob.cancel()
        client.close()
    }

    private fun createWebSocketClient(timeoutMs: Long): HttpClient {
        return createPlatformClient {
            install(HttpTimeout) {
                connectTimeoutMillis = timeoutMs
                requestTimeoutMillis = timeoutMs
                socketTimeoutMillis = timeoutMs
            }
            install(WebSockets)
        }
    }
}

/**
 * Helper class that handles Frame [fragmentation](https://www.rfc-editor.org/rfc/rfc6455#section-5.4).
 * Core expect a full binary frame to process a changeset, however the server can choose to split websocket Frames.
 * We need to buffer them until we receive the `Frame.fin == true` flag.
 *
 * Note: Core doesn't send fragmented Frames, so this buffering only needed when reading from the websocket.
 */
private class FrameBuffer(val sendDefragmentedMessageToObserver: (binaryMessage: ByteArray) -> Boolean) {
    private val buffer = mutableListOf<ByteArray>()
    private var currentSize = 0

    /**
     * @return True if we should close the Websocket after this write.
     */
    fun appendAndSend(frame: Frame): Boolean {
        if (frame.data.isNotEmpty()) {
            buffer.add(frame.data)
            currentSize += frame.data.size
        }

        if (frame.fin) {
            // Append fragmented Frames and flush the buffer
            return sendDefragmentedMessageToObserver(flush())
        }
        return false
    }

    private fun flush(): ByteArray {
        val entireFrame = ByteArray(currentSize)
        var currentIndex = 0

        for (fragment in buffer) {
            fragment.copyInto(entireFrame, destinationOffset = currentIndex)
            currentIndex += fragment.size
        }

        buffer.clear()
        currentSize = 0
        return entireFrame
    }
}
