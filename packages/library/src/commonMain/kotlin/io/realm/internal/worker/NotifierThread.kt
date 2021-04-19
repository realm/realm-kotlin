package io.realm.internal.worker

import io.realm.*
import io.realm.internal.RealmModelInternal
import io.realm.internal.freeze
import io.realm.internal.thaw
import io.realm.interop.NativePointer
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Class responsible for controlling notifications for a Realm.
 * See https://docs.google.com/document/d/1bGfjbKLD6DSBpTiVwyorSBcMqkUQWedAmmS_VAhL8QU/edit to see a big picture
 * description.
 *
 * This class wraps a Live Realm and a task queue that is responsible for handling all notifications. The workflow
 * works roughly this way:
 *
 * 0) When created this class opens a Live Realm and creates a task queue.
 * 1) A frozen Realm/Object/Query adds an Intent to register a callback on this class' task queue. This consist of
 *    the pair (FrozenOwner, Callback).
 * 2) This class wraps the pair as a ThreadSafeReference: Pair(ThreadSafeReference(owner), Callback) and put it on
 *    the task queue.
 * 3) This class polls the task queue. When getting a message, it resolves the ThreadSafeReference and registers a
 *    notification with the callback, on the, now, live object.
 * 4) The callback is invoked using the normal notification machinery available in Realm Core. When invoked, this class
 *    will freeze the result and send it through the callback
 */

@ExperimentalCoroutinesApi
internal class NotifierThread(private val owner: Realm): JobThread(owner.configuration) {
    var realm: LiveRealm? = null // TODO: Figure out type hierachy to support a public Realm, a MutableRealm and a NotifierRealm
    // TODO All notifications should be scoped by the user Realm instance. Need to figure out how to do this.
    val jobScope = CoroutineScope(SupervisorJob() + jobThread.dispatcher)

    fun realmChanged(): Flow<Pair<NativePointer, Realm.VersionId>> {
        // FIXME: Waiting for RealmInterop to have support for global Realm changed listeners
        return flowOf<Pair<NativePointer, Realm.VersionId>>()
    }

    // Listen to changes to a RealmResults
    fun <T : RealmObject<T>> resultsChanged(results: RealmResults<T>): Flow<RealmResults<T>> {
        return callbackFlow {
            var token: Cancellable
            withContext(jobThread.dispatcher) {
                val realm = getOrCreateRealm(configuration)
                val liveResults = results.thaw(realm)
                token = liveResults.addChangeListener {
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    offer(liveResults.freeze(owner))
                }
            }
            awaitClose {
                token.cancel()
            }
        }
    }

    // Listen to changes to a RealmObject
    fun <T : RealmObject<T>> objectChanged(obj: T): Flow<T> {
        return callbackFlow {
            var token: Cancellable
            withContext(jobThread.dispatcher) {
                val realm = getOrCreateRealm(configuration)
                val liveObject: RealmObject<T> = (obj as RealmModelInternal).thaw(realm)
                token = Realm.addChangeListener(liveObject as T) {
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    offer((liveObject as RealmModelInternal).freeze(owner) as T)
                }
            }
            awaitClose {
                token.cancel()
            }
        }
    }

    private fun getOrCreateRealm(configuration: RealmConfiguration): LiveRealm {
        if (realm == null) {
            realm = LiveRealm(configuration)
        }
        return realm!!
    }
}