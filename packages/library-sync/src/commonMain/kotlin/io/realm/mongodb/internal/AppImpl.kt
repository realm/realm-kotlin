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

import io.realm.internal.interop.AppCallback
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

internal class AppImpl(
    override val configuration: AppConfigurationImpl,
) : App {

    internal val nativePointer: NativePointer = RealmInterop.realm_app_get(
        configuration.nativePointer,
        initializeSyncClientConfig(),
        appFilesDirectory()
    )

    override fun currentUser(): User? {
        val currentUser = RealmInterop.realm_app_get_current_user(nativePointer)
        return currentUser?.let { UserImpl(it, this) }
    }

    override suspend fun login(credentials: Credentials): User {
        return suspendCoroutine { continuation ->
            RealmInterop.realm_app_log_in_with_credentials(
                nativePointer,
                Validation.checkType<CredentialImpl>(credentials, "credentials").nativePointer,
                object : AppCallback<NativePointer> {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || !App::class.isInstance(other)) return false

        other as App

        if (configuration != other.configuration) return false
        // FIXME The documentation and implementation differs realm-java. What should actually be the
        //  requirements
//        if (nativePointer != other.nativePointer) return false

        return true
    }

    override fun hashCode(): Int {
        var result = configuration.hashCode()
        result = 31 * result + nativePointer.hashCode()
        return result
    }


}
