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
