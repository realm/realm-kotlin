package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.util.use
import io.realm.kotlin.mongodb.Functions
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.StringFormat
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonValue

internal class FunctionsImpl(
    override val app: AppImpl,
    override val user: UserImpl,
    override val serializer: StringFormat
) : Functions {
    override suspend fun <T : Any> invoke(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T = Channel<Result<T>>(1).use { channel ->
        RealmInterop.realm_app_call_function(
            app = app.nativePointer,
            user = user.nativePointer,
            name = name,
            serializedArgs = serializer.encodeToString(ListSerializer(AnySerializer), args),
            callback = channelResultCallback(channel) { encodedObject ->
                serializer.decodeFromString(deserializationStrategy, encodedObject)
            }
        )
        return channel.receive().getOrThrow()
    }

    private suspend fun <T : Any> invokeInternal(
        name: String,
        args: List<BsonValue?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): BsonValue = TODO()
}

@OptIn(InternalSerializationApi::class)
internal object AnySerializer : KSerializer<Any?> {

    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Any") {}

    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Any?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            (value::class.serializer() as KSerializer<Any?>).serialize(encoder, value)
        }
    }

    override fun deserialize(decoder: Decoder): Any = UnsupportedOperationException("Any does not support polymorphic deserialization")
}