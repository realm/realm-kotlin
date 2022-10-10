package io.realm.kotlin.mongodb.auth

import io.realm.kotlin.types.ObjectId

public data class ApiKey public constructor(public val id: ObjectId, public val value: String?, public val name: String, public val enabled: Boolean)
