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

import io.realm.mongodb.SyncErrorCode
import io.realm.mongodb.SyncException
import kotlinx.coroutines.channels.Channel

// TODO Could be replace by lambda. See realm_app_config_new networkTransportFactory for example.
interface Callback {
    fun onChange(change: NativePointer)
}

// Callback from asynchronous sync methods. Use AppCallback<Unit> for void callbacks and
// AppCallback<NativePointer> for callbacks with native pointers to core objects.
interface AppCallback<T> {
    fun onSuccess(result: T)
    fun onError(throwable: Throwable)
}

fun <T, R> channelResultCallback(
    channel: Channel<Result<R>>,
    success: (T) -> R
): AppCallback<T> {
    return object : AppCallback<T> {
        override fun onSuccess(result: T) {
            channel.trySend(Result.success(success.invoke(result)))
        }

        override fun onError(throwable: Throwable) {
            channel.trySend(Result.failure(throwable))
        }
    }
}

interface SyncErrorCallback {
    fun onSyncError(pointer: NativePointer, throwable: SyncException)
}

// Interface used internally as a bridge between Kotlin (JVM) and JNI.
// We pass all required primitive parameters to JVM and construct the objects there, rather than
// having to do this on the JNI side, which is both a ton of boilerplate, but also expensive in
// terms of the number of JNI traversals.
internal interface JVMSyncSessionTransferCompletionCallback {
    fun onSuccess()
    fun onError(category: Int, value: Int, message: String)
}

// Interface exposed towards `library-sync`
interface SyncSessionTransferCompletionCallback {
    fun invoke(error: SyncErrorCode?)
}

interface SyncLogCallback {
    // Passes core log levels as shorts to avoid unnecessary jumping between the SDK and JNI
    fun log(logLevel: Short, message: String?)
}

fun interface CompactOnLaunchCallback {
    fun invoke(totalBytes: Long, usedBytes: Long): Boolean
}

fun interface MigrationCallback {
    fun migrate(oldRealm: NativePointer, newRealm: NativePointer, schema: NativePointer): Boolean
}
