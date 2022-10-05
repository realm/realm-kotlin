package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.mongodb.functions.Functions
import org.mongodb.kbson.BsonDocument
import org.mongodb.kbson.BsonString
import kotlin.reflect.KClass

internal class FunctionsImpl(user: User) : Functions {
    override val app: App = user.app
    override val user: User = user

    override suspend fun callFunction(name: String, vararg args: Any?): BsonDocument {
        return BsonDocument(
            mapOf(
                "foo" to BsonString("bar")
            )
        )
    }

    override suspend fun <T : Any> callFunction(
        name: String,
        args: List<Any?>,
        resultClass: KClass<T>
    ): T {
        TODO("API has not yet been decided")
    }
}
