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
import java.util.concurrent.atomic.AtomicReference

@Suppress("LongParameterList")
public class OkHttpWebsocketClient(
    private val observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedProtocols: String,
    websocketEngine: WebsocketEngine,
    timeoutMs: Long,
    private val scope: CoroutineScope,
    private val runCallback: (
        handlerCallback: RealmWebsocketHandlerCallbackPointer,
        cancelled: Boolean,
        status: WebsocketCallbackResult,
        reason: String
    ) -> Unit
) : WebSocketClient, WebSocketListener() {

    private val logger = ContextLogger("Websocket")
    private val okHttpClient: OkHttpClient = websocketEngine.getEngine(timeoutMs)
    private lateinit var webSocket: WebSocket
    private val isClosed: AtomicBoolean = AtomicBoolean(false)

    init {
        val websocketURL = "${if (isSsl) "wss" else "ws"}://$address:$port$path"
        val request: Request = Request.Builder().url(websocketURL)
            .addHeader("Sec-WebSocket-Protocol", supportedProtocols).build()

        scope.launch {
            okHttpClient.newWebSocket(request, this@OkHttpWebsocketClient)
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        logger.warn("onOpen websocket ${webSocket.request().url}, response status code ${response.code}")

        this.webSocket = webSocket

        if (!isClosed.get()) {
            response.header("Sec-WebSocket-Protocol")?.let { selectedProtocol ->
                scope.launch {
                    if (!isClosed.get()) { // The session could have been paused in the meantime which will cause the WebSocket to be destroyed, as well as the observer so avoid invoking connect on a deleted CAPIWebSocketObserver
                        observer.onConnected(selectedProtocol)
                    }
                }
            }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        if (!isClosed.get()) {
            scope.launch {
                if (!isClosed.get()) { // socket could have been closed in the meantime
                    val shouldClose: Boolean = observer.onNewMessage(bytes.toByteArray())
                    if (shouldClose) {
                        webSocket.close(
                            WebsocketErrorCode.RLM_ERR_WEBSOCKET_OK.nativeValue,
                            "websocket should be closed after last message received"
                        )
                    }
                }
            }
        }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        logger.warn("onClosing code = $code reason = $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        logger.warn("onClosed code = $code reason = $reason")
        if (!isClosed.getAndSet(true)) {
            scope.launch {
                if (!isClosed.getAndSet(true)) {
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
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        logger.warn("onFailure throwable ${t.message} websocket closed? = ${isClosed.get()}")
        if (!isClosed.get()) {
            scope.launch {
                observer.onError()
            }
        }
    }

    override fun send(message: ByteArray, handlerCallback: RealmWebsocketHandlerCallbackPointer) {
        scope.launch {
            try {
                webSocket.send(message.toByteString())
                runCallback(
                    handlerCallback, false, WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_SUCCESS, ""
                )
            } catch (e: Exception) {
                runCallback(
                    handlerCallback,
                    false,
                    WebsocketCallbackResult.RLM_ERR_SYNC_SOCKET_RUNTIME,
                    "Sending Frame exception: ${e.message}"
                )
            }
        }
    }

    override fun close() {
        logger.warn("close")
        if (!isClosed.getAndSet(true) && ::webSocket.isInitialized) { // Generalise :webSocket.isInitialized whenever it is used
            scope.launch {
                webSocket.close(
                    WebsocketErrorCode.RLM_ERR_WEBSOCKET_OK.nativeValue, "client closing websocket"
                )
            }
        }
    }
}

private object OkHttpEngine : WebsocketEngine {
    private var engine: AtomicReference<OkHttpClient> = AtomicReference(null)

    override fun shutdown() {
        engine.getAndSet(null)?.dispatcher?.executorService?.shutdownNow()
    }

    override fun <T> getEngine(timeoutMs: Long): T {
        if (engine.get() == null) { // FIXME check atomicity
            engine.set(
                OkHttpClient.Builder().connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS).build()
            )
        }

        @Suppress("UNCHECKED_CAST") return engine.get() as T
    }
}

public actual fun websocketEngine(): WebsocketEngine {
    return OkHttpEngine
}
@Suppress("LongParameterList")
public actual fun platformWebsocketClient(
    observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedProtocols: String,
    transport: RealmWebSocketTransport
): WebSocketClient {
    return OkHttpWebsocketClient(
        observer,
        path,
        address,
        port,
        isSsl,
        supportedProtocols,
        transport.engine,
        transport.timeoutMs,
        transport.scope
    ) { handlerCallback: RealmWebsocketHandlerCallbackPointer, cancelled: Boolean, status: WebsocketCallbackResult, reason: String ->
        transport.scope.launch {
            transport.runCallback(handlerCallback, cancelled, status, reason)
        }
    }
}
