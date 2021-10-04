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
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.util.Validation
import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withContext
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class AppImpl(
    configuration: AppConfigurationImpl,
) : App {

    override val configuration: AppConfigurationImpl = configuration

    val nativePointer: NativePointer =
        RealmInterop.realm_app_new(
            appConfig = configuration.nativePointer,
            basePath = appFilesDirectory()
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
                credentialsInternal.nativePointer,
                object : CinteropCallback {
                    override fun onSuccess(pointer: NativePointer) {

                        println("loging-resuming")
//                        continuation.resume(UserImpl(pointer))
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
}
