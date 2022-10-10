package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.ObjectIdImpl
import io.realm.kotlin.internal.interop.ObjectIdWrapper
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.sync.ApiKeyWrapper
import io.realm.kotlin.internal.platform.freeze
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.auth.ApiKey
import io.realm.kotlin.mongodb.auth.ApiKeyAuth
import io.realm.kotlin.types.ObjectId
import kotlinx.coroutines.channels.Channel

internal class ApiKeyAuthImpl(override val app: AppImpl, override val user: UserImpl) : ApiKeyAuth {

    override suspend fun create(name: String): ApiKey {
        Channel<Result<ApiKey>>(1).use { channel ->
            RealmInterop.realm_app_user_apikey_provider_client_create_apikey(
                app.nativePointer,
                user.nativePointer,
                name,
                channelResultCallback<ApiKeyWrapper, ApiKey>(channel) { apiKeyData ->
                    ApiKey(
                        ObjectIdImpl(apiKeyData.id),
                        apiKeyData.value,
                        apiKeyData.name,
                        !apiKeyData.disabled
                    )
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun delete(id: ObjectId) {
        Channel<Result<Unit>>(1).use { channel ->
            RealmInterop.realm_app_user_apikey_provider_client_delete_apikey(
                app.nativePointer,
                user.nativePointer,
                id as ObjectIdWrapper,
                channelResultCallback<Unit, Unit>(channel) {
                    // No-op
                }.freeze()
            )
            return channel.receive()
                .getOrThrow()
        }
    }

    override suspend fun disable(id: ObjectId) {
        TODO("Not yet implemented")
    }

    override suspend fun enable(id: ObjectId) {
        TODO("Not yet implemented")
    }

    override suspend fun fetch(id: ObjectId): ApiKey {
        TODO("Not yet implemented")
    }

    override suspend fun fetchAll(): List<ApiKey> {
        // wait with this
        TODO("Not yet implemented")
    }
}
