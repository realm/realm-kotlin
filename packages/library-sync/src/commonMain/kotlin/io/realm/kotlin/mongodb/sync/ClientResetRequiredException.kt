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
    private val error: SyncError,
    override val message: String? = error.detailedMessage
) : Throwable() {

    public val originalFilePath: String? = error.originalFilePath
    public val recoveryFilePath: String? = error.recoveryFilePath

    /**
     * Calling this method will execute the Client Reset manually instead of waiting until next app
     * restart. This will only be possible if all instances of that Realm have been closed, otherwise a {@link IllegalStateException} will
     * be thrown.
     * <p>
     * After this method returns, the backup file can be found in the location returned by {@link #getBackupFile()}.
     * The file at {@link #getOriginalFile()} have been deleted, but will be recreated from scratch next time a
     * Realm instance is opened.
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
