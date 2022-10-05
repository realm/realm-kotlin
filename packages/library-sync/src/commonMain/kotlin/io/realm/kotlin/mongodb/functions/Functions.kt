package io.realm.kotlin.mongodb.functions

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import org.mongodb.kbson.BsonDocument
import kotlin.reflect.KClass

public interface Functions {
    public val app: App
    public val user: User

    /**
     * TODO How to future proof the API for Codec/Serialization support?
     */
    public suspend fun callFunction(name: String, vararg args: Any?): BsonDocument
    public suspend fun <T : Any> callFunction(name: String, args: List<Any?>, resultClass: KClass<T>): T
}
