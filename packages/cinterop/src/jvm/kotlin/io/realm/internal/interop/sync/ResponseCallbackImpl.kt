/*
 * Copyright 2021 Realm Inc.
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

import io.realm.internal.interop.realmc

// Implementation of network response callback that is initialized from JNI and passed to
// NetworkTransport.sendRequest to signal response back to JNI
class ResponseCallbackImpl(val userData: NetworkTransport, val requestContext: Long) :
    ResponseCallback {
    override fun response(response: Response) {
        realmc.native_response_callback(requestContext, response)
    }
}
