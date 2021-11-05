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

package io.realm.internal.interop

import io.realm.mongodb.SyncException
import kotlinx.coroutines.channels.Channel

// TODO Could be replace by lambda. See realm_app_config_new networkTransportFactory for example.
interface Callback {
    fun onChange(change: NativePointer)
}

interface CinteropCallback {
    fun onSuccess(pointer: NativePointer)
    fun onError(throwable: Throwable)
}

fun <T> channelResultCallback(
    result: Channel<Result<T>>,
    success: (NativePointer) -> T
): CinteropCallback {
    return object : CinteropCallback {
        override fun onSuccess(pointer: NativePointer) {
            result.trySend(Result.success(success(pointer)))
        }

        override fun onError(throwable: Throwable) {
            result.trySend(Result.failure(throwable))
        }
    }
}

interface SyncErrorCallback {
    fun onSyncError(pointer: NativePointer, throwable: SyncException)
}

interface SyncLogCallback {
    // Passes core log levels as shorts to avoid unnecessary jumping between the SDK and JNI
    fun log(logLevel: Short, message: String?)
}
