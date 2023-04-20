package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.LoggedIn
import io.realm.kotlin.mongodb.LoggedOut
import io.realm.kotlin.mongodb.Removed
import io.realm.kotlin.mongodb.User

internal class LoggedInImpl(override val user: User) : LoggedIn
internal class LoggedOutImpl(override val user: User) : LoggedOut
internal class RemovedImpl(override val user: User) : Removed
