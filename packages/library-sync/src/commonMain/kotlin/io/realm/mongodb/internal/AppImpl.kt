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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class AppImpl(
    override val configuration: AppConfigurationImpl,
) : App {

//    // Freeze logLevel or else we'll get a mutability exception as it's accessed inside the lambda
//    private val loggerFactory: () -> CoreLogger = configuration.logLevel.freeze().let { logLevel ->
//        // Freeze the actual logger instance too since it will be used from another thread
//        { createDefaultSystemLogger("SYNC", logLevel).freeze() }
//    }

//    private val logger: RealmLogger = configuration.logLevel.freeze().let { logLevel ->
//        // Freeze the actual logger instance too since it will be used from another thread
//        createDefaultSystemLogger("SYNC", logLevel).freeze()
//    }

    private val nativePointer: NativePointer = RealmInterop.realm_sync_client_config_new()
        .also { syncClientConfig ->
            // Initialize client configuration first
//            RealmInterop.realm_sync_client_config_set_logger_factory(
//                syncClientConfig,
//                loggerFactory
//            )
//            RealmInterop.realm_sync_client_config_set_log_level(
//                syncClientConfig,
//                configuration.logLevel.priority
//            )
            RealmInterop.realm_sync_client_config_set_metadata_mode(
                syncClientConfig,
                configuration.metadataMode
            )
        }.let { syncClientConfig ->
            // Get the app with the initialized configuration
            RealmInterop.realm_app_get(
                configuration.nativePointer,
                syncClientConfig,
                appFilesDirectory()
            )
        }

    override suspend fun login(credentials: Credentials): User {
        return suspendCoroutine { continuation ->
            RealmInterop.realm_app_log_in_with_credentials(
                nativePointer,
                Validation.checkType<CredentialImpl>(credentials, "credentials").nativePointer,
                object : CinteropCallback {
                    override fun onSuccess(pointer: NativePointer) {
                        continuation.resume(UserImpl(pointer, this@AppImpl))
                    }

                    override fun onError(throwable: Throwable) {
                        continuation.resumeWithException(throwable)
                    }
                }
            )
        }
    }
}
