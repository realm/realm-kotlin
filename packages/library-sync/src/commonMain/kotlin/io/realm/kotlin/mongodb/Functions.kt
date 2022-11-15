package io.realm.kotlin.mongodb

import io.realm.kotlin.mongodb.exceptions.AppException
import io.realm.kotlin.mongodb.internal.Ejson
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer
import org.mongodb.kbson.BsonValue

public interface Functions {
    public val app: App
    public val user: User

    /**
     * Invokes an App Services Application function.
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
 * Invokes a Realm app services function.
 *
 * Reified convenience wrapper of [Functions.invoke].
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
 * Invokes a Realm app services function.
 *
 * Reified convenience wrapper of [Functions.invoke].
 */
public suspend inline fun <reified T : Any?> Functions.invoke(
    name: String,
    args: List<Any?>
): T = invoke<T>(
    name = name,
    args = args,
    deserializationStrategy = serializerOrDefault()
)

public inline fun <reified T : Any?> serializerOrDefault(): KSerializer<T> =
    if (T::class == Any::class) {
        Ejson.serializersModule.serializer<BsonValue>()
    } else {
        Ejson.serializersModule.serializer<T>()
    } as KSerializer<T>
