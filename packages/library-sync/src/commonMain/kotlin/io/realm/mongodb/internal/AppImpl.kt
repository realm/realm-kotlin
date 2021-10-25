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

    internal val nativePointer: NativePointer = RealmInterop.realm_sync_client_config_new()
        .also { syncClientConfig ->
            RealmInterop.realm_sync_client_config_set_metadata_mode(
                syncClientConfig,
                configuration.metadataMode
            )
        }.let { syncClientConfig ->
            RealmInterop.realm_app_get(
                appConfig = configuration.nativePointer,
                syncClientConfig = syncClientConfig,
                basePath = appFilesDirectory()
            )
        }

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
