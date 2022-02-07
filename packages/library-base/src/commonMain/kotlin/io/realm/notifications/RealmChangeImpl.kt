package io.realm.notifications

import io.realm.BaseRealm

internal class InitialRealmImpl<R : BaseRealm>(override val realm: R) : InitialRealm<R>

internal class UpdatedRealmImpl<R : BaseRealm>(override val realm: R) : UpdatedRealm<R>