package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.internal.interop.RealmAppPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.sync.SyncError

/**
 * Class encapsulating information needed for handling a Client Reset event.
 *
 * **See:** [Client Reset](https://www.mongodb.com/docs/atlas/app-services/sync/error-handling/client-resets/)
 * for more information about when and why Client Reset occurs and how to deal with it.
 *
 */
public class ClientResetRequiredException constructor(
    private val appPointer: RealmAppPointer,
    private val error: SyncError
) : Throwable(message = error.detailedMessage) {

    public val originalFilePath: String = error.originalFilePath!!
    public val recoveryFilePath: String = error.recoveryFilePath!!

    /**
     * Calling this method will execute the Client Reset manually instead of waiting until the next
     * app restart.
     *
     * After this method returns, the backup file can be found in the location returned by
     * [recoveryFilePath]. The file at [originalFilePath] will have been deleted, but will be
     * recreated from scratch next time a Realm instance is opened.
     *
     * **WARNING:** To guarantee the backup file is generated correctly all Realm instances
     * associated to the session in which this error is generated **must be closed**. Not doing so
     * might incur in unexpected file system errors.
     *
     * @throws IllegalStateException if not all instances have been closed.
     */
    public fun executeClientReset() {
        RealmInterop.realm_sync_immediately_run_file_actions(
            appPointer,
            requireNotNull(error.originalFilePath) { "Original path cannot be null." }
        )
    }
}
