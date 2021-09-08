package io.realm.interop

interface NetworkTransport {

    val authorizationHeaderName: String?
    val customHeaders: Map<String, String>

    fun sendRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
        usesRefreshToken: Boolean
    ): Response
}

data class Response(
    val httpResponseCode: Int,
    val customResponseCode: Int,
    val headers: Map<String, String>,
    val body: String
)
