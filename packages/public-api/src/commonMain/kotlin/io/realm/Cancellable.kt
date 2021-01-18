package io.realm

// Cancel an underlying Realm Task
interface Cancellable {
    fun cancel(): Boolean
    fun isCancelled(): Boolean
}