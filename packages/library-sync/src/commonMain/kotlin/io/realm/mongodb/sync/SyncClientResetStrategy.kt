package io.realm.mongodb.sync

import io.realm.MutableRealm
import io.realm.TypedRealm

/**
 * Interface that defines a generic sync client reset strategy.
 */
public interface SyncClientResetStrategy

/**
 * Strategy that automatically resolves a Client Reset by discarding any unsynced data but otherwise
 * keeps the realm open. Any changes will be reported through the normal collection and object
 * notifications.
 *
 * A synced realm may need to be reset because the MongoDB Realm Server encountered an error and had
 * to be restored from a backup, or because it has been too long since the client connected to the
 * server so the server has rotated the logs.
 *
 * The Client Reset thus occurs because the server does not have all the information required to
 * bring the client fully up to date.
 *
 * The reset process for unsynced changes is as follows: when a client reset is triggered the
 * [onBeforeReset] callback is invoked, providing an instance of the realm before the reset and
 * another instance after the reset, being both read-only. Once the reset has concluded,
 * [onAfterReset] will be invoked with an instance of the final realm.
 *
 * In the event that discarding the unsynced data is not enough to resolve the reset the [onError]
 * callback will be invoked.
 */
public interface DiscardUnsyncedChangesStrategy : SyncClientResetStrategy {

    /**
     * Callback that indicates a Client Reset is about to happen. It receives a frozen instance
     * of the realm that will be reset.
     *
     * @param realm frozen [TypedRealm] in its state before the reset.
     */
    public fun onBeforeReset(realm: TypedRealm)

    /**
     * Callback invoked once the Client Reset happens. It receives two Realm instances: a frozen one
     * displaying the state before the reset and a regular one with the current state that can be
     * used to recover objects from the reset.
     *
     * @param before [TypedRealm] frozen realm before the reset.
     * @param after [MutableRealm] a realm after the reset.
     */
    public fun onAfterReset(before: TypedRealm, after: MutableRealm)

    /**
     * Callback that indicates the seamless Client reset couldn't complete. It should be handled
     * as in [ManuallyRecoverUnsyncedChangesStrategy.onClientReset].
     *
     * @param session [SyncSession] during which this error happened.
     * @param error [ClientResetRequiredError] the specific Client Reset error.
     */
    public fun onError(session: SyncSession, error: ClientResetRequiredError)
}
