package io.realm.mongodb.sync

import io.realm.internal.interop.RealmAppPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.SyncError

/**
 * TODO docs and move to its own file
 */
public class ClientResetRequiredError constructor(
    private val appPointer: RealmAppPointer,
    private val error: SyncError
) {

    public val originalFilePath: String? = error.originalFilePath
    public val recoveryFilePath: String? = error.recoveryFilePath
    public val detailedMessage: String? = error.detailedMessage

    /**
     * TODO
     */
    public fun executeClientReset() {
        RealmInterop.realm_sync_immediately_run_file_actions(
            appPointer,
            requireNotNull(error.originalFilePath) { "Original path cannot be null." }
        )
    }
}
