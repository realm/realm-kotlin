package io.realm.mongodb.internal

import io.ktor.client.HttpClient

/**
 * Cache HttpClient on Android and JVM.
 * https://github.com/realm/realm-kotlin/issues/480 only seem to be a problem on macOS.
 */
actual class HttpClientCache actual constructor(timeoutMs: Long) {
    private val client = createClient(timeoutMs)
    actual fun getClient(): HttpClient {
        return client
    }
}