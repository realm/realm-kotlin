package io.realm.mongodb.internal

import io.ktor.client.HttpClient

/**
 * On MacOS we need to re-create the Client on each request due to
 * https://github.com/realm/realm-kotlin/issues/480.
 */
actual class HttpClientCache actual constructor(timeoutMs: Long) {

    private val timeout: Long = timeoutMs

    actual fun getClient(): HttpClient {
        return createClient(timeout)
    }
}
