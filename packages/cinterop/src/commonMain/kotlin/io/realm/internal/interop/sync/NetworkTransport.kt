/*
 * Copyright 2020 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

    fun sendRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String,
        callback: ResponseCallback
    )
}

fun interface ResponseCallback {
    fun response(response: Response)
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
