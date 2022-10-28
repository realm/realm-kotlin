package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.Functions
import io.realm.kotlin.mongodb.User
import kotlinx.serialization.StringFormat
import kotlinx.serialization.modules.SerializersModule
import kotlin.reflect.KClass

internal class FunctionsImpl(
    override val app: App,
    override val user: User,
    override val serializer: StringFormat
) : Functions {
    override suspend fun <T : Any> callFunction(
        name: String,
        vararg args: Any?,
        resultClass: KClass<T>
    ): T {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> callFunction(
        name: String,
        args: List<Any?>,
        resultClass: KClass<T>
    ): T {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> callFunction(
        name: String,
        vararg args: Any?,
        resultClass: KClass<T>,
        customSerializerModule: SerializersModule
    ): T {
        TODO("Not yet implemented")
    }

    override suspend fun <T : Any> callFunction(
        name: String,
        args: List<Any?>,
        resultClass: KClass<T>,
        customSerializerModule: SerializersModule
    ): T {
        TODO("Not yet implemented")
    }
}