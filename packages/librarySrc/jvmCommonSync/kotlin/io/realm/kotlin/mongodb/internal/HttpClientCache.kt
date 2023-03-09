@file:JvmName("HttpClientCacheJVM")
package io.realm.kotlin.mongodb.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.logging.Logger

/**
 * Cache HttpClient on Android and JVM.
 * https://github.com/realm/realm-kotlin/issues/480 only seem to be a problem on macOS.
 */
internal actual class HttpClientCache actual constructor(timeoutMs: Long, customLogger: Logger?) {
    private val httpClient: HttpClient by lazy { createClient(timeoutMs, customLogger) }
    actual fun getClient(): HttpClient {
        return httpClient
    }
    actual fun close() {
        httpClient.close()
    }
}

public actual fun createPlatformClient(block: HttpClientConfig<*>.() -> Unit): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                retryOnConnectionFailure(true)
            }
        }
        this.apply(block)
    }
}
