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

package io.realm.mongodb

import io.realm.internal.platform.runBlocking
import io.realm.interop.Callback
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.mongodb.internal.KtorNetworkTransport
import io.realm.mongodb.internal.NetworkTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel

/**
 * TODO
 */
interface App {

    val appConfiguration: AppConfiguration
    val syncConfiguration: SyncConfiguration?
    val nativePointer: NativePointer

    suspend fun login(credentials: Credentials): Result<User>

    companion object {
        fun create(
            configuration: AppConfiguration,
            syncConfiguration: SyncConfiguration
        ): App = AppImpl(configuration, syncConfiguration)

        fun create(appId: String): App = AppImpl(
            appConfiguration = AppConfigurationImpl(
                appId
            ),
            syncConfiguration = null // TODO
        )
    }
}

/**
 * TODO
 */
private class AppImpl(
    override val appConfiguration: AppConfiguration,
    override val syncConfiguration: SyncConfiguration? = null // TODO
) : App {

    override val nativePointer: NativePointer = RealmInterop.realm_app_new(
        appConfiguration.nativePointer,
        syncConfiguration?.nativePointer
    )

    override suspend fun login(credentials: Credentials): Result<User> {
        // TODO is this the right way?
        val channel = Channel<Result<User>>()

        RealmInterop.realm_app_log_in_with_credentials(
            nativePointer,
            credentials.nativePointer,
            object : Callback {
                override fun onChange(change: NativePointer) {
                    runBlocking {
                        channel.send(Result.success(UserImpl(change)))
                    }
                }
            }
        )

        return channel.receive()
            .also { channel.close() }
    }

    fun getNetworkTransport(): NetworkTransport {
        return KtorNetworkTransport(
            timeoutMs = 5000,
            dispatcher = Dispatchers.Default    // TODO extract from app config
        )
    }
}
