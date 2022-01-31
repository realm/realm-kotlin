package io.realm.notifications

import io.realm.BaseRealm

sealed interface RealmChange<R : BaseRealm> {
    enum class State {
        INITIAL,
        UPDATED
    }
    val state: State
    /**
     * Returns the newest version of the Realm.
     */
    val realm: R
}
interface InitialRealm<R : BaseRealm> : RealmChange<R>
interface UpdatedRealm<R : BaseRealm> : RealmChange<R>

internal class InitialRealmImpl<R : BaseRealm>(override val realm: R) : InitialRealm<R> {
    override val state: RealmChange.State
        get() = RealmChange.State.INITIAL
}

internal class UpdatedRealmImpl<R : BaseRealm>(override val realm: R) : UpdatedRealm<R> {
    override val state: RealmChange.State
        get() = RealmChange.State.UPDATED
}
