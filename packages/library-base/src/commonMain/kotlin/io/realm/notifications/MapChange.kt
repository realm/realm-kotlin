package io.realm.notifications

sealed interface MapChange<K : String, V : String> {
    enum class State {
        INITIAL,
        UPDATED,
        DELETED
    }

    /**
     * Returns the state represented by this change. See [io.realm.notifications.MapChange.State]
     * for a description of the different states a changeset can be in.
     */
    val state: State

    /**
     * Returns the newest state of the Map being observed. If an error occurred when calculating
     * changes or the parent object owning the map was deleted, `null` is returned.
     */

    @Suppress("ForbiddenComment")
    // FIXME: Should probably be RealmMap<V> when available.
    val map: Map<K, V>
}

interface InitialMap<K : String, V : String> : MapChange<K, V>
interface UpdatedMap<K : String, V : String> : MapChange<K, V> {
    /**
     * Array containing the keys of entries that have been deleted in the previous version of the map.
     */
    val deletions: Array<K>
    /**
     * Array containing the keys that have been inserted in the previous version of the map.
     */
    val insertions: Array<K>
    /**
     * Array containing the keys that have been modified in the previous version of the map.
     */
    val changes: Array<K>
}
interface DeletedMap<K : String, V : String> : MapChange<K, V>
