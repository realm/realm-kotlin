package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.mongodb.App
import io.realm.kotlin.mongodb.syncSession

/**
 * A <i>Device Sync</i> manager responsible for controlling all sync sessions across all realms
 * associated with a given [App] instance. For session functionality associated with a single
 * realm, see [syncSession].
 *
 * @see App.sync
 * @see io.realm.kotlin.mongodb.syncSession
 */
public interface Sync {

    /**
     * Returns whether or not any sync sessions are still active.
     */
    public val hasSyncSessions: Boolean

    /**
     * Realm will automatically detect when a device gets connectivity after being offline and
     * resume syncing. However, as some of these checks are performed using incremental backoff,
     * this will in some cases not happen immediately.
     *
     * In those cases it can be beneficial to call this method manually, which will force all
     * sessions to attempt to reconnect immediately and reset any timers they are using for
     * incremental backoff.
     */
    public fun reconnect()

    /**
     * Calling this method will block until all sync sessions for a given [App] has terminated.
     *
     * Closing a Realm will terminate the sync session, but it is not synchronous as Realms
     * communicate with their sync session using an asynchronous communication channel. This
     * has the effect that trying to delete a Realm right after closing it will sometimes throw
     * an [IllegalStateException]. Using this method can be a way to ensure it is safe to delete
     * the file.
     */
    public fun waitForSessionsToTerminate()
}
