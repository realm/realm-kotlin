/*
 * Copyright 2022 Realm Inc.
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
package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.ErrorCode
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.sync.ApiKeyWrapper
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.auth.ApiKey
import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.mongodb.exceptions.ServiceException
import kotlinx.coroutines.channels.Channel
import org.mongodb.kbson.BsonObjectId

internal class ApiKeyAuthImpl(override val app: AppImpl, override val user: UserImpl) : ApiKeyAuth {

    private fun unwrap(apiKeyData: ApiKeyWrapper): ApiKey {
        return ApiKey(
            apiKeyData.id,
            apiKeyData.value,
            apiKeyData.name,
            !apiKeyData.disabled
        )
    }

    override suspend fun create(name: String): ApiKey {
        try {
            Channel<Result<ApiKey>>(1).use { channel ->
                RealmInterop.realm_app_user_apikey_provider_client_create_apikey(
                    app.nativePointer,
                    user.nativePointer,
                    name,
                    channelResultCallback<ApiKeyWrapper, ApiKey>(channel) { apiKeyData ->
                        unwrap(apiKeyData)
                    }
                )
                return channel.receive()
                    .getOrThrow()
            }
        } catch (ex: ServiceException) {
            // TODO in the future, change to comparing error codes rather than messages
            if (ex.errorCode == ErrorCode.RLM_ERR_INVALID_PARAMETER ||
                ex.message?.contains("[Service][Unknown(4351)] 'name' is a required string.") == true // FIXME Unknown because the server does not provide with a error code
            ) {
                throw IllegalArgumentException(ex.message!!)
            } else {
                throw ex
            }
        }
    }

    override suspend fun delete(id: BsonObjectId) {
        try {
            Channel<Result<Unit>>(1).use { channel ->
                RealmInterop.realm_app_user_apikey_provider_client_delete_apikey(
                    app.nativePointer,
                    user.nativePointer,
                    id,
                    channelResultCallback<Unit, Unit>(channel) {
                        // No-op
                    }
                )
                return channel.receive().getOrThrow()
            }
        } catch (ex: ServiceException) {
            if (ex.errorCode == ErrorCode.RLM_ERR_API_KEY_NOT_FOUND) {
                // No-op
            } else {
                throw ex
            }
        }
    }

    override suspend fun disable(id: BsonObjectId) {
        try {
            Channel<Result<Unit>>(1).use { channel ->
                RealmInterop.realm_app_user_apikey_provider_client_disable_apikey(
                    app.nativePointer,
                    user.nativePointer,
                    id,
                    channelResultCallback<Unit, Unit>(channel) {
                        // No-op
                    }
                )
                return channel.receive()
                    .getOrThrow()
            }
        } catch (ex: ServiceException) {
            if (ex.errorCode == ErrorCode.RLM_ERR_API_KEY_NOT_FOUND) {
                throw IllegalArgumentException(ex.message!!)
            } else {
                throw ex
            }
        }
    }

    override suspend fun enable(id: BsonObjectId) {
        try {
            Channel<Result<Unit>>(1).use { channel ->
                RealmInterop.realm_app_user_apikey_provider_client_enable_apikey(
                    app.nativePointer,
                    user.nativePointer,
                    id,
                    channelResultCallback<Unit, Unit>(channel) {
                        // No-op
                    }
                )
                return channel.receive()
                    .getOrThrow()
            }
        } catch (ex: ServiceException) {
            if (ex.errorCode == ErrorCode.RLM_ERR_API_KEY_NOT_FOUND) {
                throw IllegalArgumentException(ex.message!!)
            } else {
                throw ex
            }
        }
    }

    override suspend fun fetch(id: BsonObjectId): ApiKey? {
        try {
            Channel<Result<ApiKey?>>(1).use { channel ->
                RealmInterop.realm_app_user_apikey_provider_client_fetch_apikey(
                    app.nativePointer,
                    user.nativePointer,
                    id,
                    channelResultCallback<ApiKeyWrapper, ApiKey?>(channel) { apiKeyData: ApiKeyWrapper ->
                        unwrap(apiKeyData)
                    }
                )
                return channel.receive()
                    .getOrThrow()
            }
        } catch (ex: ServiceException) {
            if (ex.errorCode == ErrorCode.RLM_ERR_API_KEY_NOT_FOUND) {
                return null
            } else {
                throw ex
            }
        }
    }

    override suspend fun fetchAll(): List<ApiKey> {
        Channel<Result<List<ApiKey>>>(1).use { channel ->
            RealmInterop.realm_app_user_apikey_provider_client_fetch_apikeys(
                app.nativePointer,
                user.nativePointer,
                channelResultCallback<Array<ApiKeyWrapper>, List<ApiKey>>(channel) { apiKeys: Array<ApiKeyWrapper> ->
                    val result = mutableListOf<ApiKey>()
                    apiKeys.map { apiKeydata: ApiKeyWrapper ->
                        result.add(
                            unwrap(apiKeydata)
                        )
                    }
                    result
                }
            )
            return channel.receive()
                .getOrThrow()
        }
    }
}
