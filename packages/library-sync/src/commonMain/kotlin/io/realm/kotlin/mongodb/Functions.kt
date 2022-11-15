package io.realm.kotlin.mongodb

import io.realm.kotlin.mongodb.internal.BsonEncoderHelper
import io.realm.kotlin.mongodb.exceptions.AppException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonValue

public interface Functions {
    public val app: App
    public val user: User

    /**
     * Invokes a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param deserializationStrategy Deserialization strategy for decoding the results.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any?> invoke(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T
}

/**
 * TODO document
 */
public suspend inline fun <reified T : Any?> Functions.call(
    name: String,
    vararg args: Any?
): T = invoke<T>(
    name = name,
    args = args.toList(),
    deserializationStrategy = serializerOrDefault()
)

/**
 * TODO document
 */
public suspend inline fun <reified T : Any> Functions.invoke(
    name: String,
    args: List<Any?>
): T = invoke<T>(
    name = name,
    args = args,
    deserializationStrategy = serializerOrDefault()
)

public inline fun <reified T : Any?> serializerOrDefault(): KSerializer<T> =
    if (T::class == Any::class) {
        BsonEncoderHelper.serializersModule.serializer<BsonValue>()
    } else {
        BsonEncoderHelper.serializersModule.serializer<T>()
    } as KSerializer<T>
