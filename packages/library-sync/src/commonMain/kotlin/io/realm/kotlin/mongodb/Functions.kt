package io.realm.kotlin.mongodb

import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass
import io.realm.kotlin.mongodb.exceptions.AppException

public interface Functions {
    public val app: App
    public val user: User
    public val serializer: StringFormat // TODO required to be public?

    /**
     * Call a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The class for the functions response.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any> callFunction(
        name: String,
        vararg args: Any?,
        resultClass: KClass<T>
    ): T

    /**
     * Call a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The class for the functions response.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any> callFunction(
        name: String,
        args: List<Any?>,
        resultClass: KClass<T>
    ): T

    /**
     * Call a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The class for the functions response.
     * @param customSerializerModule custom serializer to be used when parsing arguments and response.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any> callFunction(
        name: String,
        vararg args: Any?,
        resultClass: KClass<T>,
        customSerializerModule: SerializersModule
    ): T

    /**
     * Call a Realm app services function.
     *
     * @param name Name of the Realm function to call.
     * @param args Arguments to the Realm function.
     * @param resultClass  The class for the functions response.
     * @param customSerializerModule custom serializer to be used when parsing arguments and response.
     * @param T The type for the functions response.
     * @return Result of the Realm function.
     *
     * @throws AppException if the request failed in some way.
     */
    public suspend fun <T : Any> callFunction(
        name: String,
        args: List<Any?>,
        resultClass: KClass<T>,
        customSerializerModule: SerializersModule
    ): T
}

public inline fun <reified T : Any> Functions.callFunction22(
    name: String,
    vararg args: Any?
): T = callFunction<T>(name, listOf(), T::class)
