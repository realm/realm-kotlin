/*
 * Copyright 2022 Realm Inc.
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

import io.realm.internal.interop.SyncSessionTransferCompletionCallback

// Interface used internally as a bridge between Kotlin (JVM) and JNI.
// We pass all required primitive parameters to JVM and construct the objects there, rather than
// having to do this on the JNI side, which is both a ton of boilerplate, but also expensive in
// terms of the number of JNI traversals.
internal class JVMSyncSessionTransferCompletionCallback(
    private val callback: SyncSessionTransferCompletionCallback
) {
    fun onSuccess() {
        callback.invoke(null)
    }
    fun onError(category: Int, value: Int, message: String) {
        callback.invoke(SyncErrorCode(SyncErrorCodeCategory.of(category), value, message))
    }
}
