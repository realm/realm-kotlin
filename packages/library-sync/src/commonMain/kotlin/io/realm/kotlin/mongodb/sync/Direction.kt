package io.realm.kotlin.mongodb.sync

/**
 * A **direction** indicates whether a given [Progress]-flow created with
 * [SyncSession.progress] is reporting changes in ongoing uploads or downloads.
 */
public enum class Direction {
    /**
     * Used to pass to [SyncSession.progress] to create a flow that reports [Progress]
     * in the [SyncSession]'s downloads.
     */
    DOWNLOAD,
    /**
     * Used to pass to [SyncSession.progress] to create a flow that reports [Progress]
     * in the [SyncSession]'s uploads.
     */
    UPLOAD,
}
