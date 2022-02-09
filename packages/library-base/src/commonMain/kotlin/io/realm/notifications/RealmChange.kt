package io.realm.notifications

import io.realm.BaseRealm

/**
 * A [RealmChange] describes the type of changes that can be observed on a realm.
 */
sealed interface RealmChange<R : BaseRealm> {
    /**
     * Returns the newest version of the Realm.
     */
    val realm: R
}
/**
 * [InitialRealm] describes the initial event observed on a Realm flow. It contains the Realm instance
 * it was subscribed to.
 */
interface InitialRealm<R : BaseRealm> : RealmChange<R>

/**
 * [UpdatedRealm] describes a Realm update event to be observed on a Realm flow after the [InitialRealm].
 * It contains a Realm instance of the updated Realm.
 */
interface UpdatedRealm<R : BaseRealm> : RealmChange<R>
