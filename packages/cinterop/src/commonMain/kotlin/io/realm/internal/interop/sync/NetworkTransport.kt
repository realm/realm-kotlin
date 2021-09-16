package io.realm.internal.interop.sync

interface NetworkTransport {

    companion object {
        const val GET = "get"
        const val POST = "post"
        const val PATCH = "patch"
        const val PUT = "put"
        const val DELETE = "delete"
    }

    val authorizationHeaderName: String?
    val customHeaders: Map<String, String>

    // FIXME https://github.com/realm/realm-kotlin/issues/450
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
) {
    // Returns the HTTP headers in a JNI friendly way where it is being serialized to a
    // String array consisting of pairs of { key , value } pairs.
    fun getJNIFriendlyHeaders(): Array<String?> {
        val jniHeaders = arrayOfNulls<String>(headers.size * 2)
        var i = 0
        for ((key, value) in headers) {
            jniHeaders[i] = key
            jniHeaders[i + 1] = value
            i += 2
        }
        return jniHeaders
    }
}
