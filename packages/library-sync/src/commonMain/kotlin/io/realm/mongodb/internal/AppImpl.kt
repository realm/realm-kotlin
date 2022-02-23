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

import io.realm.internal.interop.CoreLogLevel
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.SyncLogCallback
import io.realm.internal.interop.channelResultCallback
import io.realm.internal.platform.appFilesDirectory
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.freeze
import io.realm.internal.util.Validation
import io.realm.internal.util.use
import io.realm.log.LogLevel
import io.realm.mongodb.App
import io.realm.mongodb.Credentials
import io.realm.mongodb.EmailPasswordAuth
import io.realm.mongodb.User
import kotlinx.coroutines.channels.Channel

// TODO Public due to being a transitive dependency to UserImpl
public class AppImpl(
    override val configuration: AppConfigurationImpl,
) : App {

    internal val nativePointer: NativePointer = RealmInterop.realm_app_get(
        configuration.nativePointer,
        initializeSyncClientConfig(),
        appFilesDirectory()
    )

    override val emailPasswordAuth: EmailPasswordAuth by lazy { EmailPasswordAuth(nativePointer) }

    override val currentUser: User?
        get() = RealmInterop.realm_app_get_current_user(nativePointer)
            ?.let { UserImpl(it, this) }

    override suspend fun login(credentials: Credentials): User {
        // suspendCoroutine doesn't allow freezing callback capturing continuation
        // ... and cannot be resumed on another thread (we probably also want to guarantee that we
        // are resuming on the same dispatcher), so run our own implementation using a channel
        Channel<Result<User>>(1).use { channel ->
            RealmInterop.realm_app_log_in_with_credentials(
                nativePointer,
                Validation.checkType<CredentialImpl>(credentials, "credentials").nativePointer,
                channelResultCallback<NativePointer, User>(channel) { userPointer ->
                    UserImpl(userPointer, this)
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
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
}
