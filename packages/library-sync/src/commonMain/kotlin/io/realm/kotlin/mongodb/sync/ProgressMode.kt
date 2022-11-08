package io.realm.kotlin.mongodb.sync

/**
 * A **progress mode** is used to select which notifications are received from
 * [SyncSession.progress].
 */
public enum class ProgressMode {

    /**
     * Used to pass to [SyncSession.progress] to create a flow that will record the size of the
     * current outstanding changes not transferred, and will only continue
     * to report progress updates until those changes have been either downloaded or uploaded. After
     * that the progress listener will not report any further changes.
     *
     * Progress reported in this mode will only ever increase.
     *
     * **NOTE:** The [Flow] returned by [SyncSession.progress] will complete when the transfer is
     * done, i.e. after emitting a [Progress] with
     * [Progress.isTransferComplete] being `true`.
     */
    CURRENT_CHANGES,

    /**
     * Used to pass to [SyncSession.progress] to create a flow that reports the size of outstanding
     * changes not yet transferred and will continue to report progress changes, even if changes
     * are being added after the listener was registered.
     *
     * Progress reported in this mode can both increase and decrease, e.g. if large amounts of data is
     * written after registering the listener.
     */
    INDEFINITELY,
}
