package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.Functions
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.DeserializationStrategy
import org.mongodb.kbson.serialization.Bson

internal class FunctionsImpl(
    override val app: AppImpl,
    override val user: UserImpl
) : Functions {
    override suspend fun <T : Any?> invoke(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T = Channel<Result<T>>(1).use { channel ->
            RealmInterop.realm_app_call_function(
                app = app.nativePointer,
                user = user.nativePointer,
                name = name,
                serializedArgs = Ejson.encodeToString(args),
                callback = channelResultCallback(channel) { ejsonEncodedObject: String ->
                    // First we decode from ejson -> BsonValue
                    // then from BsonValue -> T
                    Ejson.decodeFromBsonValue(
                        deserializationStrategy = deserializationStrategy,
                        bsonValue = Bson(ejsonEncodedObject)
                    )
                }
            )
        return channel.receive().getOrThrow()
    }
}
