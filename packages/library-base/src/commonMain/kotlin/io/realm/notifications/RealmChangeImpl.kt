package io.realm.notifications

import io.realm.BaseRealm

internal data class InitialRealmImpl<R : BaseRealm>(override val realm: R) : InitialRealm<R> {
    override val state: RealmChange.State
        get() = RealmChange.State.INITIAL
}

internal data class UpdatedRealmImpl<R : BaseRealm>(override val realm: R) : UpdatedRealm<R> {
    override val state: RealmChange.State
        get() = RealmChange.State.UPDATED
}
