package io.realm.kotlin.mongodb

import kotlinx.serialization.StringFormat
import io.realm.kotlin.mongodb.exceptions.AppException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

public interface Functions {
    public val app: App
    public val user: User
    public val serializer: StringFormat

    /**
     * Invokes a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The class for the functions response.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any> invoke(
        name: String,
        args: List<Any?>,
        deserializationStrategy: DeserializationStrategy<T>
    ): T
}

/**
 * TODO document
 */
public suspend inline fun <reified T : Any> Functions.call(
    name: String,
    vararg args: Any?
): T = invoke(
    name = name,
    args = args.asList(),
    deserializationStrategy = serializer.serializersModule.serializer()
)

/**
 * TODO document
 */
public suspend inline fun <reified T : Any> Functions.invoke(
    name: String,
    args: List<Any?>
): T = invoke(
    name = name,
    args = args,
    deserializationStrategy = serializer.serializersModule.serializer()
)

