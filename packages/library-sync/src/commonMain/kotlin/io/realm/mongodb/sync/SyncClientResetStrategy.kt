package io.realm.mongodb.sync

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.TypedRealm
import io.realm.kotlin.mongodb.sync.SyncSession

/**
 * Interface that defines a generic sync client reset strategy. It can be either
 * [ManuallyRecoverUnsyncedChangesStrategy] or [DiscardUnsyncedChangesStrategy].
 */
public sealed interface SyncClientResetStrategy

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
 * callback will be invoked, allowing to manually resolve the reset as it would be done in
 * [ManuallyRecoverUnsyncedChangesStrategy.onClientReset].
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
     * @param exception [ClientResetRequiredException] the specific Client Reset error.
     */
    public fun onError(session: SyncSession, exception: ClientResetRequiredException)
}

/**
 * Strategy to manually resolve a Client Reset.
 *
 * A synced realm may need to be reset because the MongoDB Realm Server encountered an error and had
 * to be restored from a backup, or because it has been too long since the client connected to the
 * server so the server has rotated the logs.
 *
 * The Client Reset thus occurs because the server does not have all the information required to
 * bring the client fully up to date.
 *
 * The manual reset process is as follows: the local copy of the realm is copied into a recovery
 * directory for safekeeping and then deleted from the original location. The next time the realm
 * for that URL is opened it will automatically be re-downloaded from MongoDB Realm, and can be used
 * as usual.
 *
 * Data written to the realm after the local copy of itself diverged from the backup remote copy
 * will be present in the local recovery copy of the Realm file. The re-downloaded realm will
 * initially contain only the data present at the time the realm was backed up on the server.
 *
 * The client reset process can be initiated in one of two ways:
 *
 *  1. Run [ClientResetRequiredException.executeClientReset] manually. All Realm instances must be
 *  closed before this method is called.
 *
 *  2. If Client Reset isn't executed manually, it will automatically be carried out the next time
 *  all Realm instances have been closed and re-opened. This will most likely be when the app is
 *  restarted.
 *
 * **WARNING:**
 * Any writes to the Realm file between this callback and Client Reset has been executed, will not
 * be synchronized to MongoDB Realm. Those changes will only be present in the backed up file. It is
 * therefore recommended to close all open Realm instances as soon as possible.
 */
public interface ManuallyRecoverUnsyncedChangesStrategy : SyncClientResetStrategy {

    /**
     * Callback that indicates a Client Reset has happened. This should be handled as quickly as
     * possible as any further changes to the realm will not be synchronized with the server and
     * must be moved manually from the backup realm to the new one.
     *
     * @param session [SyncSession] during which this error happened.
     * @param exception [ClientResetRequiredException] the specific Client Reset error.
     */
    public fun onClientReset(session: SyncSession, exception: ClientResetRequiredException)
}
