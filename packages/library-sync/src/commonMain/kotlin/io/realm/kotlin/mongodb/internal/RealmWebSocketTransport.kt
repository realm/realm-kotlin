package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmWebsocketHandlerCallbackPointer
import io.realm.kotlin.internal.interop.sync.CancellableTimer
import io.realm.kotlin.internal.interop.sync.WebSocketClient
import io.realm.kotlin.internal.interop.sync.WebSocketObserver
import io.realm.kotlin.internal.interop.sync.WebSocketTransport
import io.realm.kotlin.internal.interop.sync.WebsocketEngine
import io.realm.kotlin.internal.util.CoroutineDispatcherFactory
import io.realm.kotlin.internal.util.DispatcherHolder
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

public class RealmWebSocketTransport(
    public val timeoutMs: Long
) : WebSocketTransport {
    // we need a single thread dispatcher to act like an event loop
    private val dispatcherHolder: DispatcherHolder = CoroutineDispatcherFactory.managed("RealmWebsocketTransport").create()
    public val scope: CoroutineScope = CoroutineScope(dispatcherHolder.dispatcher)

    public val engine: WebsocketEngine by lazy { websocketEngine(timeoutMs) }

    private val websocketClients = mutableListOf<WebSocketClient>()

    override fun post(handlerCallback: RealmWebsocketHandlerCallbackPointer) {
        scope.launch {
            (this as Job).invokeOnCompletion { completionHandler: Throwable? ->
                when (completionHandler) {
                    // Only run the callback successfully if it was not cancelled in the meantime
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
        val atomicCallback: AtomicRef<RealmWebsocketHandlerCallbackPointer?> =
            atomic(handlerCallback)
        return CancellableTimer(
            scope.launch {
                delay(delayInMilliseconds)
                atomicCallback.getAndSet(null)?.run { // this -> callback pointer
                    runCallback(this)
                }
            }
        ) {
            scope.launch {
                atomicCallback.getAndSet(null)?.run { // this -> callback pointer
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
        supportedSyncProtocols: String,
    ): WebSocketClient = platformWebsocketClient(
        observer, path, address, port, isSsl, supportedSyncProtocols, this
    ).also {
        websocketClients.add(it)
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
        // Notify clients that the transport is tearing down
        websocketClients.forEach { it.close() }

        // Shutdown Websocket Engine shared by same App
        if (websocketClients.isNotEmpty()) {
            // Shutdown is an event that should be posted to the event loop, otherwise
            // premature closing of the websocket could occur. Even if the transport is tearing down
            // we still want to close gracefully the connection.
            scope.launch {
                engine.shutdown()
            }
        }

        // Closing the coroutine dispatcher should also be done via the event loop, to avoid prematurely
        // closing the thread executor of a running coroutine (this will throw a `InterruptedException`)
        scope.launch {
            dispatcherHolder.close()
            cancel()
        }
        websocketClients.clear()
    }
}

public expect fun websocketEngine(timeoutMs: Long): WebsocketEngine
@Suppress("LongParameterList")
public expect fun platformWebsocketClient(
    observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedSyncProtocols: String,
    transport: RealmWebSocketTransport
): WebSocketClient
