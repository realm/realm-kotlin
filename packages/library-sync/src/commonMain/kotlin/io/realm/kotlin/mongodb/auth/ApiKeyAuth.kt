package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.User
import io.realm.kotlin.types.ObjectId

public interface ApiKeyAuth {
    public suspend fun create(name: String): ApiKey
    public suspend fun delete(id: ObjectId)
    public suspend fun disable(id: ObjectId)
    public suspend fun enable(id: ObjectId)
    public suspend fun fetch(id: ObjectId): ApiKey?
    public suspend fun fetchAll(): List<ApiKey>
    public val user: User
    public val app: App
}
