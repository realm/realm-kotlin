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

import io.realm.internal.platform.appFilesDirectory
import io.realm.interop.Callback
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.mongodb.App
import io.realm.mongodb.AppConfiguration
import io.realm.mongodb.Credentials
import io.realm.mongodb.User
import kotlinx.coroutines.suspendCancellableCoroutine

internal class AppImpl(
    override val configuration: AppConfiguration,
) : App {

    val nativePointer: NativePointer =
        RealmInterop.realm_app_new(
            appConfig = configuration.nativePointer,
            basePath = appFilesDirectory()
        )

    override suspend fun login(credentials: Credentials): Result<User> {
        return RealmInterop.runCatching {
            suspendCancellableCoroutine<User> {
                realm_app_log_in_with_credentials(
                    nativePointer,
                    credentials.nativePointer,
                    object : Callback {
                        override fun onChange(change: NativePointer) {
                            it.resumeWith(Result.success(UserImpl(change)))
                        }
                    }
                )
            }
        }
    }
}
