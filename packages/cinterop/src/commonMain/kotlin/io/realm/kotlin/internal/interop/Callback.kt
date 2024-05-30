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

package io.realm.kotlin.internal.interop

import io.realm.kotlin.internal.interop.sync.AppError
import io.realm.kotlin.internal.interop.sync.CoreConnectionState
import io.realm.kotlin.internal.interop.sync.CoreSubscriptionSetState
import io.realm.kotlin.internal.interop.sync.SyncError

// TODO Could be replace by lambda. See realm_app_config_new networkTransportFactory for example.
interface Callback<T : RealmNativePointer> {
    fun onChange(change: T)
}

// Callback from asynchronous sync methods. Use AppCallback<Unit> for void callbacks and
// AppCallback<NativePointer> for callbacks with native pointers to core objects.
interface AppCallback<T> {
    fun onSuccess(result: T)
    fun onError(error: AppError)
}

fun interface SyncErrorCallback {
    fun onSyncError(pointer: RealmSyncSessionPointer, error: SyncError)
}

// Interface exposed towards `library-sync`
interface SyncSessionTransferCompletionCallback {
    fun invoke(error: CoreError?)
}

interface LogCallback {
    // Passes core log levels as shorts to avoid unnecessary jumping between the SDK and JNI
    fun log(logLevel: Short, categoryValue: String, message: String?)
}

interface SyncBeforeClientResetHandler {
    fun onBeforeReset(realmBefore: FrozenRealmPointer)
}

interface SyncAfterClientResetHandler {
    fun onAfterReset(
        realmBefore: FrozenRealmPointer,
        realmAfter: LiveRealmPointer,
        didRecover: Boolean
    )
}

fun interface CompactOnLaunchCallback {
    fun invoke(totalBytes: Long, usedBytes: Long): Boolean
}

fun interface MigrationCallback {
    fun migrate(
        oldRealm: FrozenRealmPointer,
        newRealm: LiveRealmPointer,
        schema: RealmSchemaPointer
    )
}

fun interface SubscriptionSetCallback {
    fun onChange(state: CoreSubscriptionSetState)
}

// The underlying Core implementation can also pass in Realm pointer, but since it is not
// useful during construction, we omit it from this callback as it is only used as a signal.
fun interface DataInitializationCallback {
    fun invoke()
}

fun interface AsyncOpenCallback {
    fun invoke(error: Throwable?)
}

fun interface ProgressCallback {
    fun onChange(progressEstimate: Double)
}

fun interface ConnectionStateChangeCallback {
    fun onChange(oldState: CoreConnectionState, newState: CoreConnectionState)
}

interface SyncThreadObserver {
    // Should return the name of the Java Sync thread.
    fun threadName(): String
    // Called when the underlying Sync thread is created
    fun onCreated()
    // Called when the underlying Sync thread is destroyed
    fun onDestroyed()
    // Called when an error occurred on the underlying Sync thread
    fun onError(error: String)
}
