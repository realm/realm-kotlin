package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.Functions
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.StringFormat
import kotlinx.serialization.encodeToString

internal class FunctionsImpl(
    override val app: AppImpl,
    override val user: UserImpl,
    override val serializer: StringFormat
) : Functions {
    override suspend fun <T : Any> call(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T = Channel<Result<T>>(1).use { channel ->
        RealmInterop.realm_app_call_function(
            app = app.nativePointer,
            user = user.nativePointer,
            name = name,
            serializedArgs = serializer.encodeToString(args),
            callback = channelResultCallback(channel) { encodedObject ->
                serializer.decodeFromString(deserializationStrategy, encodedObject)
            }
        )
        return channel.receive().getOrThrow()
    }
}
