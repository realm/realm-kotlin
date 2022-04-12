package io.realm.internal.interop.sync

import io.realm.internal.interop.SyncSessionTransferCompletionCallback
import io.realm.mongodb.SyncErrorCode

// Interface used internally as a bridge between Kotlin (JVM) and JNI.
// We pass all required primitive parameters to JVM and construct the objects there, rather than
// having to do this on the JNI side, which is both a ton of boilerplate, but also expensive in
// terms of the number of JNI traversals.
internal class JVMSyncSessionTransferCompletionCallback(
    private val callback: SyncSessionTransferCompletionCallback
) {
    fun onSuccess() {
        callback.invoke(null)
    }
    fun onError(category: Int, value: Int, message: String) {
        callback.invoke(SyncErrorCode(SyncErrorCodeCategory.of(category), value, message))
    }
}
