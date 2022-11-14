package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.Functions
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.mongodb.kbson.BsonValue

internal class FunctionsImpl(
    override val app: AppImpl,
    override val user: UserImpl
) : Functions {
    override suspend fun <T : Any?> invoke(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T = Channel<Result<T>>(1).use { channel ->
        with(BsonEncoderHelper) {
            RealmInterop.realm_app_call_function(
                app = app.nativePointer,
                user = user.nativePointer,
                name = name,
                serializedArgs = encodeToString(args),
                callback = channelResultCallback(channel) { encodedObject ->
                    decodeFromBsonValue(
                        deserializationStrategy = deserializationStrategy,
                        bsonValue = Json.decodeFromString<BsonValue>(encodedObject)
                    )
                }
            )
        }
        return channel.receive().getOrThrow()
    }
}
