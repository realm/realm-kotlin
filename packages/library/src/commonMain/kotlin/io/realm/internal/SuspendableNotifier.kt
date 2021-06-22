package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.Realm
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.VersionId
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import io.realm.isValid
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Class responsible for controlling notifications for a Realm. It does this by wrapping a live Realm on which
 * notifications can be registered. Since all objects that is otherwise exposed to users are frozen, they need
 * to be thawed when reaching the live Realm.
 *
 * For Lists and Objects, this can result in the object no longer existing. In this case, Flows complete
 * immediately and changelisteners will never be triggered (Is this the semantics we want? What do Room do?).
 *
 * Notifications are not allowed on live objects. But it is assumed that other layers check that invariant before
 * methods on this class is called.
 */
internal class SuspendableNotifier(private val owner: Realm, private val dispatcher: CoroutineDispatcher) {

    companion object {
        val NO_OP_NOTIFICATION_TOKEN = object : Cancellable {
            override fun cancel() { /* Do Nothing */ }
        }
    }

    // Must only be accessed from the dispatchers thread
    private val realm: NotifierRealm by lazy {
        NotifierRealm(owner.configuration, dispatcher)
    }
    private val shouldClose = kotlinx.atomicfu.atomic<Boolean>(false)
    private val notificationMutex = Mutex(false)

    fun checkIsSendingNotification(errorMessage: String) {
        // FIXME
    }

    /**
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    fun realmChanged(): Flow<Pair<NativePointer, VersionId>> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                token.value = addRealmChangedListener { frozenRealm ->
                    val result = trySend(frozenRealm)
                    if (!result.isSuccess) {
                        // FIXME What to do if this fails?
                        throw IllegalStateException("Notification could not be handled: $result")
                    }
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    fun addRealmChangedListener(callback: Callback<Pair<NativePointer, VersionId>>): Cancellable {
        // FIXME Waiting for RealmInterop to have support for global Realm changed
        return object : Cancellable {
            override fun cancel() {
            }
        }
    }

    /**
     * Listen to changes to a RealmResults
     */
    fun <T : RealmObject> resultsChanged(results: RealmResults<T>): Flow<RealmResults<T>> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                val newToken = addResultsChangedListener(results) { frozenResults ->
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    val result = trySend(frozenResults)
                    checkResult(result)
                }
                token.value = newToken
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    // Verify that notifications emitted to Streams are handled in an uniform manner
    private fun checkResult(result: ChannelResult<Unit>) {
        if (result.isClosed) {
            // If the Flow was closed, we assume it is on purpose, so avoid raising an exception.
            return
        }
        if (!result.isSuccess) {
            // TODO Is there a better way to handle this?
            throw IllegalStateException("Notification could not be handled: $result")
        }
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Register a change listener on a live RealmResults. All objects returned in the callback are frozen.
     */
    fun <T : RealmObject> addResultsChangedListener(results: RealmResults<T>, callback: Callback<RealmResults<T>>): Cancellable {
        val liveResults = results.thaw(realm.realmReference)
        val token = RealmInterop.realm_results_add_notification_callback(
            liveResults.result,
            object : io.realm.interop.Callback {
                override fun onChange(collectionChanges: NativePointer) {
                    // FIXME The Realm should have been frozen in `realmChanged`, but this isn't supported yet.
                    //  Instead we create the frozen version ourselves (which is correct, but pretty inefficient)
                    val frozenRealm = RealmReference(owner, RealmInterop.realm_freeze(realm.realmReference.dbPointer))

                    // Notifications need to be delivered with the version they where created on, otherwise
                    // the fine-grained notification data might be out of sync.
                    val frozenResults = liveResults.freeze(frozenRealm)
                    callback.onChange(frozenResults)
                }
            }
        )
        return NotificationToken(callback, token)
    }

    /**
     * Listen to changes to a RealmObject through a [Flow]. If the object is deleted, null is emitted and the flow will complete.
     */
    fun <T : RealmObject> objectChanged(obj: T): Flow<T?> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                token.value = addObjectChangedListener(obj) { frozenObj ->
                    val result = trySend(frozenObj)
                    checkResult(result)
                    if (frozenObj == null) {
                        close()
                    }
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Listen to changes to a RealmObject through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     */
    fun <T : RealmObject> addObjectChangedListener(obj: T, callback: Callback<T?>): Cancellable {
        val liveObject: RealmObjectInternal? = (obj as RealmObjectInternal).thaw(realm.realmReference.owner) as RealmObjectInternal?
        if (liveObject == null || !liveObject.isValid()) {
            return NO_OP_NOTIFICATION_TOKEN
        }
        val token = RealmInterop.realm_object_add_notification_callback(
            liveObject.`$realm$ObjectPointer`!!,
            object : io.realm.interop.Callback {
                override fun onChange(objectChanges: NativePointer) {
                    // Realm should already have been updated with the latest version
                    // So `owner` should as a minimum be at the same version as the notification Realm.
                    if (!liveObject.isValid()) {
                        callback.onChange(null)
                    } else {
                        callback.onChange(liveObject.freeze(owner.realmReference))
                    }
                }
            }
        )
        return NotificationToken(callback, token)
    }

    /**
     * Listen to changes to a RealmList through a [Flow]. If the list is deleted the flow will complete.
     */
    fun <T : RealmObject> listChanged(list: List<T?>): Flow<List<T?>?> {
        TODO("Implement and convert method to use RealmList when available")
    }

    /**
     * Listen to changes to a RealmList through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     */
    fun <T : RealmObject> addListChangedListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        TODO("Implement and convert method to use RealmList when available")
    }

    fun close() {
        checkIsSendingNotification("Cannot close the Realm inside a notification block.")
        // FIXME Is it safe at all times to close a Realm?
        // Especially test what happens during notification handling
        realm.close()
//
//        runBlocking {
//            // TODO OPTIMIZE We are currently awaiting any running transaction to finish before
//            //  actually closing the realm, as we cannot schedule something to run on the dispatcher
//            //  and closing the realm from another thread during a transaction causes race
//            //  conditions/crashed. Maybe signal this faster by canceling the users scope of the
//            //  transaction, etc.
//            shouldClose.value = true
//            notificationMutex.withLock {
//                realm.close()
//            }
//        }
    }
}
