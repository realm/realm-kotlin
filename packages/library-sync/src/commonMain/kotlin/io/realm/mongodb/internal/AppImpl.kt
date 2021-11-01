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

package io.realm.mongodb.internal

import io.realm.internal.interop.CinteropCallback
import io.realm.internal.interop.CoreLogLevel
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.SyncLogCallback
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.util.Validation
import io.realm.log.LogLevel
import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class AppImpl(
    override val configuration: AppConfigurationImpl,
) : App {

    private val nativePointer: NativePointer = RealmInterop.realm_app_get(
        configuration.nativePointer,
        initializeSyncClientConfig(),
        appFilesDirectory()
    )

    override suspend fun login(credentials: Credentials): User {
        val credentialsInternal: CredentialImpl = Validation.checkType(credentials, "credentials")
//        return withContext(configuration.networkTransportDispatcher) {
//            suspendCoroutine { continuation: Continuation<User> ->
//                RealmInterop.realm_app_log_in_with_credentials(
//                    nativePointer,
//                    credentialsInternal.nativePointer,
//                    object : CinteropCallback {
//                        override fun onSuccess(pointer: NativePointer) {
//
//                            println("loging-resuming")
//                            continuation.resume(UserImpl(pointer))
//                            println("loging-resumed")
////                            channel.send(UserImpl(pointer))
//                        }
//
//                        override fun onError(throwable: Throwable) {
//                            println("loging-erroring: $throwable")
//                            continuation.resumeWithException(throwable)
//                            println("loging-errored")
//                        }
//                    }
//                )
//            }
//        }

        val channel = Channel<User>(1)
        val job1: Job = Job()
        val job2: CoroutineContext = Job()

        val coroutineScope: CoroutineScope = CoroutineScope(coroutineContext)
        coroutineScope.async {
            RealmInterop.realm_app_log_in_with_credentials(
                nativePointer,
                Validation.checkType<CredentialImpl>(credentials, "credentials").nativePointer,
                object : CinteropCallback {
                    override fun onSuccess(pointer: NativePointer) {

                        println("loging-resuming")
//                        continuation.resume(UserImpl(pointer, this@AppImpl))
                        channel.trySend(UserImpl(pointer))
                        println("loging-resumed")
//                            channel.send(UserImpl(pointer))
                    }

                    override fun onError(throwable: Throwable) {
                        println("loging-erroring: $throwable")
//                        continuation.resumeWithException(throwable)
                        println("loging-errored")
                    }
                }
            )
        }
        return channel.receive()
//        return withContext(configuration.networkTransportDispatcher) {
//            suspendCoroutine { continuation: Continuation<User> ->
//                RealmInterop.realm_app_log_in_with_credentials(
//                    nativePointer,
//                    credentialsInternal.nativePointer,
//                    object : CinteropCallback {
//                        override fun onSuccess(pointer: NativePointer) {
//
//                            println("loging-resuming")
//                            continuation.resume(UserImpl(pointer))
//                            println("loging-resumed")
////                            channel.send(UserImpl(pointer))
//                        }
//
//                        override fun onError(throwable: Throwable) {
//                            println("loging-erroring: $throwable")
//                            continuation.resumeWithException(throwable)
//                            println("loging-errored")
//                        }
//                    }
//                )
//            }
//        }
//
//        }
//        return channel.receive()
//        return suspendCoroutine { continuation ->
//            RealmInterop.realm_app_log_in_with_credentials(
//                nativePointer,
//                credentialsInternal.nativePointer,
//                object : CinteropCallback {
//                    override fun onSuccess(pointer: NativePointer) {
//                        continuation.resume(UserImpl(pointer))
//                    }
//
//                    override fun onError(throwable: Throwable) {
//                        continuation.resumeWithException(throwable)
//                    }
//                }
//            )
//        }
    }

    private fun initializeSyncClientConfig(): NativePointer =
        RealmInterop.realm_sync_client_config_new()
            .also { syncClientConfig ->
                // TODO use separate logger for sync or piggyback on config's?
                val syncLogger = createDefaultSystemLogger("SYNC", configuration.log.logLevel)

                // Initialize client configuration first
                RealmInterop.realm_sync_client_config_set_log_callback(
                    syncClientConfig,
                    object : SyncLogCallback {
                        override fun log(logLevel: Short, message: String?) {
                            val coreLogLevel = CoreLogLevel.valueFromPriority(logLevel)
                            syncLogger.log(LogLevel.fromCoreLogLevel(coreLogLevel), message ?: "")
                        }
                    }
                )
                RealmInterop.realm_sync_client_config_set_log_level(
                    syncClientConfig,
                    CoreLogLevel.valueFromPriority(configuration.log.logLevel.priority.toShort())
                )
                RealmInterop.realm_sync_client_config_set_metadata_mode(
                    syncClientConfig,
                    configuration.metadataMode
                )
            }
}
