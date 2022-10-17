package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.types.ObjectId
/**
 * Class representing an API key for a User. An API can be used to represent the
 * user when logging instead of using email and password.
 * Note that a keys value is only available when the key is created, after that it is not
 * visible. So anyone creating an API key is responsible for storing it safely after that.
 * @param ObjectId, an id uniquely identifying the key.
 * @param value, the value of this key. Only returned when the key is created.
 * @param name, the name of the key.
 * @param enabled, whether the key is enabled or not.
 */
public data class ApiKey public constructor(public val id: ObjectId, public val value: String?, public val name: String, public val enabled: Boolean)
