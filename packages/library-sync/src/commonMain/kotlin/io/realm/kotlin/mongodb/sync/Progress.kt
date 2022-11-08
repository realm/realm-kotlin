package io.realm.kotlin.mongodb.sync

/**
 * A **progress indicator** emitted by flows created from [SyncSession.progress].
 */
public data class Progress(
    /**
     * Total number of bytes that has been transferred by the [SyncSession].
     */
    val transferredBytes: ULong,
    /**
     * Total number of transferable bytes (bytes that have been transferred + pending bytes not
     * yet transferred).
     */
    val transferableBytes: ULong
) {
    /**
     * Property indicating if all pending bytes have been transferred.
     *
     * If the [Progress]-flow was created with [ProgressMode.CURRENT_CHANGES] then
     * this will be `true` and the flow will be completed.
     *
     * If the [Progress]-flow was created with [ProgressMode.INDEFINITELY] then the
     * flow can continue to emit events with `isTransferComplete = false` for subsequent events
     * after returning a progress indicator with `isTransferComplete = true`.
     */
    public val isTransferComplete: Boolean = transferredBytes >= transferableBytes
}
