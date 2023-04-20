package io.realm.kotlin.mongodb.internal

import io.realm.kotlin.mongodb.LoggedIn
import io.realm.kotlin.mongodb.LoggedOut
import io.realm.kotlin.mongodb.Removed
import io.realm.kotlin.mongodb.User

internal class LoggedInImpl(override val user: User) : LoggedIn {
    override fun didLogIn(): Boolean = (user.state == User.State.LOGGED_IN)
    override fun didLogOut(): Boolean = (user.state == User.State.LOGGED_OUT || user.state == User.State.REMOVED)
}

internal class LoggedOutImpl(override val user: User) : LoggedOut {
    override fun didLogIn(): Boolean = (user.state == User.State.LOGGED_IN)
    override fun didLogOut(): Boolean = (user.state == User.State.LOGGED_OUT || user.state == User.State.REMOVED)
}

internal class RemovedImpl(override val user: User) : Removed {
    override fun didLogIn(): Boolean = (user.state == User.State.LOGGED_IN)
    override fun didLogOut(): Boolean = (user.state == User.State.LOGGED_OUT || user.state == User.State.REMOVED)
}
