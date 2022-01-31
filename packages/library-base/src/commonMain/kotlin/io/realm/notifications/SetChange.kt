package io.realm.notifications

sealed interface SetChange<V> {
    enum class State {
        INITIAL,
        UPDATED,
        DELETED
    }

    /**
     * Returns the state represented by this change. See [io.realm.notifications.SetChange.State]
     * for a description of the different states a changeset can be in.
     */
    val state: State

    /**
     * Returns the newest version of the Set being observed. If an error occured when calculating
     * changes or the parent object owning the set was deleted, `null` is returned.
     */

    @Suppress("ForbiddenComment")
    // FIXME: Should probably be RealmSet<V> when available.
    val set: Set<V>
}

interface InitialSet<V> : SetChange<V>
interface UpdatedSet<V> : SetChange<V> {
    /**
     * The number of entries that have been inserted.
     */
    val numberOfInsertions: Int

    /**
     * The number of entries that have been deleted.
     */
    val numberOfDeletions: Int
}

interface DeletedSet<V> : SetChange<V>
