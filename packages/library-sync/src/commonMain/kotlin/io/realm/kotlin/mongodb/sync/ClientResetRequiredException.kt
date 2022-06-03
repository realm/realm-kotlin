package io.realm.kotlin.mongodb.sync

import io.realm.kotlin.internal.interop.RealmAppPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.sync.SyncError

/**
 * TODO docs and move to its own file
 */
public class ClientResetRequiredException constructor(
    private val appPointer: RealmAppPointer,
    private val error: SyncError,
    override val cause: Throwable? = null
) : Throwable() {

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
