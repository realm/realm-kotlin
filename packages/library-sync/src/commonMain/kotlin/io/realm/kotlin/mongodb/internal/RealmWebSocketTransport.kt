package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmWebsocketHandlerCallbackPointer
import io.realm.kotlin.internal.interop.sync.CancellableTimer
import io.realm.kotlin.internal.interop.sync.WebSocketClient
import io.realm.kotlin.internal.interop.sync.WebSocketObserver
import io.realm.kotlin.internal.interop.sync.WebSocketTransport
import io.realm.kotlin.internal.interop.sync.WebsocketEngine
import io.realm.kotlin.internal.util.DispatcherHolder
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

public class RealmWebSocketTransport(
    public val timeoutMs: Long,
    public val dispatcherHolder: DispatcherHolder
) : WebSocketTransport {
    private val transportJob: CompletableJob by lazy { Job() }
    public val scope: CoroutineScope by lazy { CoroutineScope(dispatcherHolder.dispatcher + transportJob) }
    private val websocketClients = mutableListOf<WebSocketClient>()
    public val engine: WebsocketEngine by lazy { websocketEngine() }

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
        supportedProtocols: String,
    ): WebSocketClient = platformWebsocketClient(
        observer, path, address, port, isSsl, supportedProtocols, this
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
        websocketClients.forEach { it.close() }
        websocketClients.clear()
        engine.shutdown()
        scope.cancel()
    }
}

public expect fun websocketEngine(): WebsocketEngine
@Suppress("LongParameterList")
public expect fun platformWebsocketClient(
    observer: WebSocketObserver,
    path: String,
    address: String,
    port: Long,
    isSsl: Boolean,
    supportedProtocols: String,
    transport: RealmWebSocketTransport
): WebSocketClient
