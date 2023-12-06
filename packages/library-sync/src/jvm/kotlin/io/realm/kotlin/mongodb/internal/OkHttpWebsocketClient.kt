package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.ContextLogger
import io.realm.kotlin.internal.interop.RealmWebsocketHandlerCallbackPointer
import io.realm.kotlin.internal.interop.sync.WebSocketClient
import io.realm.kotlin.internal.interop.sync.WebSocketObserver
import io.realm.kotlin.internal.interop.sync.WebsocketCallbackResult
import io.realm.kotlin.internal.interop.sync.WebsocketEngine
import io.realm.kotlin.internal.interop.sync.WebsocketErrorCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random

@Suppress("LongParameterList")
public class OkHttpWebsocketClient(
    private val observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedSyncProtocols: String,
    websocketEngine: WebsocketEngine,
    /**
     * We use this single threaded scope as an event loop to run all events on the same thread,
     * in the order they were queued in. This include callback to Core via [observer] as well as calls
     * from Core to send a Frame or close the Websocket.
     */
    private val scope: CoroutineScope,
    private val runCallback: (
        handlerCallback: RealmWebsocketHandlerCallbackPointer,
        cancelled: Boolean,
        status: WebsocketCallbackResult,
        reason: String
    ) -> Unit
) : WebSocketClient, WebSocketListener() {

    private val logger = ContextLogger("Websocket-${Random.nextInt()}")

    /**
     * [WebsocketEngine] responsible of establishing the connection, sending and receiving websocket Frames.
     */
    private val okHttpClient: OkHttpClient = websocketEngine.getInstance()

    private lateinit var webSocket: WebSocket

    /**
     * Indicates that the websocket is in the process of being closed by Core.
     * We can still send enqueued Frames like 'unbind' but we should not communicate back any incoming messages to
     * Core via the [observer].
     */
    private val observerIsClosed: AtomicBoolean = AtomicBoolean(false)

    /**
     * Indicates that the websocket is effectively closed. No message should be sent or received after this.
     */
    private val isClosed: AtomicBoolean = AtomicBoolean(false)

    private val protocolSelectionHeader = "Sec-WebSocket-Protocol"

    init {
        val websocketURL = "${if (isSsl) "wss" else "ws"}://$address:$port$path"
        val request: Request = Request.Builder().url(websocketURL)
            .addHeader(protocolSelectionHeader, supportedSyncProtocols)
            .build()

        scope.launch {
            okHttpClient.newWebSocket(request, this@OkHttpWebsocketClient)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        logger.debug("onOpen websocket ${webSocket.request().url}")

        this.webSocket = webSocket

        response.header(protocolSelectionHeader)?.let { selectedProtocol ->
            runIfNotClosing {
                observer.onConnected(selectedProtocol)
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        logger.debug("onMessage: ${bytes.toByteArray().decodeToString()} isClosed = ${isClosed.get()} observerIsClosed = ${observerIsClosed.get()}")

        runIfNotClosing {
            val shouldClose: Boolean = observer.onNewMessage(bytes.toByteArray())
            if (shouldClose) {
                webSocket.close(
                    WebsocketErrorCode.RLM_ERR_WEBSOCKET_OK.nativeValue,
                    "websocket should be closed after last message received"
                )
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        logger.debug("onClosing code = $code reason = $reason isClosed = ${isClosed.get()} observerIsClosed = ${observerIsClosed.get()}")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        logger.debug("onClosed code = $code reason = $reason isClosed = ${isClosed.get()} observerIsClosed = ${observerIsClosed.get()}")

        isClosed.set(true)

        runIfNotClosing {
            // It's important to rely properly the error code from the server.
            // The server will report auth errors (and a few other error types)
            // as websocket application-level errors after establishing the socket, rather than failing at the HTTP layer.
            // since the websocket spec does not allow the HTTP status code from the response to be
            // passed back to the client from the websocket implementation (example instruct a refresh token
            // via a 401 HTTP response is not possible) see https://jira.mongodb.org/browse/BAAS-10531.
            // In order to provide a reasonable response that the Sync Client can react upon, the private range of websocket close status codes
            // 4000-4999, can be used to return a more specific error.
            WebsocketErrorCode.of(code)?.let { errorCode ->
                observer.onClose(
                    true, errorCode, reason
                )
            } ?: run {
                observer.onClose(
                    true,
                    WebsocketErrorCode.RLM_ERR_WEBSOCKET_FATAL_ERROR,
                    "Unknown error code $code. original reason $reason"
                )
            }
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        logger.debug("onFailure throwable '${t.message}' isClosed = ${isClosed.get()} observerIsClosed = ${observerIsClosed.get()}")

        runIfNotClosing {
            observer.onError()
        }
    }

    override fun send(message: ByteArray, handlerCallback: RealmWebsocketHandlerCallbackPointer) {
        logger.debug("send: ${message.decodeToString()} isClosed = ${isClosed.get()} observerIsClosed = ${observerIsClosed.get()}")

        // send any queued Frames even if the Core observer is closed, but only if the websocket is still open, this can be a message like 'unbind'
        // which instruct the Sync server to terminate the Sync Session (server will respond by 'unbound').
        if (!isClosed.get()) {
            scope.launch {
                try {
                    if (!isClosed.get()) { // double check that the websocket is still open before sending.
                        webSocket.send(message.toByteString())
                        runCallback(
                            handlerCallback,
                            observerIsClosed.get(), // if the Core observer is closed we run this callback as cancelled (to free underlying resources)
                            WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS,
                            ""
                        )
                    } else {
                        runCallback(
                            handlerCallback,
                            observerIsClosed.get(), // if the Core observer is closed we run this callback as cancelled (to free underlying resources)
                            WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_CONNECTION_CLOSED,
                            "Connection already closed"
                        )
                    }
                } catch (e: Exception) {
                    runCallback(
                        handlerCallback,
                        observerIsClosed.get(), // if the Core observer is closed we run this callback as cancelled (to free underlying resources)
                        WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_RUNTIME,
                        "Sending Frame exception: ${e.message}"
                    )
                }
            }
        } else {
            scope.launch {
                runCallback(
                    handlerCallback,
                    observerIsClosed.get(), // if the Core observer is closed we run this callback as cancelled (to free underlying resources)
                    WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_CONNECTION_CLOSED,
                    "Connection already closed"
                )
            }
        }
    }

    override fun close() {
        logger.debug("close")
        observerIsClosed.set(true)

        if (::webSocket.isInitialized) {
            scope.launch {
                if (!isClosed.get()) {
                    webSocket.close(
                        WebsocketErrorCode.RLM_ERR_WEBSOCKET_OK.nativeValue, "client closed websocket"
                    )
                }
            }
        }
    }

    /**
     * Runs the [block] inside the transport [scope] only if Core didn't initiate the Websocket closure.
     */
    private fun runIfNotClosing(block: () -> Unit) {
        if (!observerIsClosed.get()) { // if Core has already closed the websocket there's no point in scheduling this coroutine.
            scope.launch {
                // The session could have been paused/closed in the meantime which will cause the WebSocket to be destroyed, as well as the 'observer',
                // so avoid invoking any Core observer callback on a deleted 'CAPIWebSocketObserver'.
                if (!observerIsClosed.get()) { // only run if Core observer is still valid (i.e Core didn't close the websocket yet)
                    block()
                }
            }
        }
    }
}

private class OkHttpEngine(timeoutMs: Long) : WebsocketEngine {
    private var engine: OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .build()

    override fun shutdown() {
        engine.dispatcher.executorService.shutdown()
    }

    override fun <T> getInstance(): T {
        @Suppress("UNCHECKED_CAST") return engine as T
    }
}

public actual fun websocketEngine(timeoutMs: Long): WebsocketEngine {
    return OkHttpEngine(timeoutMs)
}

@Suppress("LongParameterList")
public actual fun platformWebsocketClient(
    observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedSyncProtocols: String,
    transport: RealmWebSocketTransport
): WebSocketClient {
    return OkHttpWebsocketClient(
        observer,
        path,
        address,
        port,
        isSsl,
        supportedSyncProtocols,
        transport.engine,
        transport.scope
    ) { handlerCallback: RealmWebsocketHandlerCallbackPointer, cancelled: Boolean, status: WebsocketCallbackResult, reason: String ->
        transport.scope.launch {
            transport.runCallback(handlerCallback, cancelled, status, reason)
        }
    }
}
