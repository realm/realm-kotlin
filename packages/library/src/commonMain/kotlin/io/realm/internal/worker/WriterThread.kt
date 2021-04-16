package io.realm.internal.worker

import io.realm.RealmConfiguration
import io.realm.interop.NativePointer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.launch

/**
 * Class responsible for writes to a Realm.
 * See https://docs.google.com/document/d/1bGfjbKLD6DSBpTiVwyorSBcMqkUQWedAmmS_VAhL8QU/edit to see
 * a big picture description.
 *
 * This class wraps a Live MutableRealm, a background event-loop thread and a Dispatcher that can
 * coordinate access to the thread. The workflow works roughly this way:
 *
 * 0) When created this class opens a MutableRealm on the background thread.
 * 1) Writes from the user Realm is run as coroutine using the background thread Dispatcher. This
 *    mean that all writes are run in a FIFO-like manner.
 * 2) When a write completes, we freeze a copy of the state of the MutableRealm and send it back to
 *    the user Realm.
 * 3) The user Realm will then update its internal DBPointer, so the user Realm reflects the latest
 *    write. So when the write completes, users can query their Realm and see the result of the
 *    write.
 * 4) Notifications for any changelisteners will afterwards be calculated by the [NotifierThread].
 */
sealed class WriteResult
data class Success(val returnValue: Any): WriteResult()
data class Error(val exception: Exception): WriteResult()

// TODO: Technically we don't need a Looper Thread for a writer Realm. Any Thread will do
//  as we don't support notifications inside writes anyway.
class WriterThread(configuration: RealmConfiguration): JobThread(configuration) {
    var realm: MutableRealm? = null

    // Should only be called from within the JobThread itself to make sure the Realm is created
    // on the correct thread
    fun getOrCreateRealm(): MutableRealm {
        if (realm == null) {
            realm = MutableRealm(configuration)
        }
        return realm!!
    }

    override fun close() {
        super.close()
        realm?.close()
    }
}
