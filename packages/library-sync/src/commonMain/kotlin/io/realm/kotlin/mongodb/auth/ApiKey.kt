package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.types.ObjectId
/**
 * Class representing an API key for a [User]. An API key can be used to represent a
 * user when logging in instead of using email and password.
 * Note that the value of a key will only be available immediately after the key is created, after
 * which point it is not visible anymore. This means that keys returned by [ApiKeyAuth.fetch] and
 * [ApikeyAuth.fetchall] will have a `null` [value]. Anyone creating an API key is responsible for
 * storing it safely after that.
 * @param id, an [ObjectId] uniquely identifying the key.
 * @param value, the value of this key, only returned when the key is created, `null` otherwise.
 * @param name, the name of the key.
 * @param enabled, whether the key is enabled or not.
 */
public data class ApiKey public constructor(public val id: ObjectId, public val value: String?, public val name: String, public val enabled: Boolean)
