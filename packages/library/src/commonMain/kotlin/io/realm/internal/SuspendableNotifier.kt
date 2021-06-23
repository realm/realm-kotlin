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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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

    // FIXME Work-around for the global Realm changed listener not working
    // This Flow exposes a stream of changes to the owner Realm
    private val _realmChanged = MutableSharedFlow<RealmReference>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.SUSPEND)

    // Must only be accessed from the dispatchers thread
    private val realm: NotifierRealm by lazy {
        NotifierRealm(owner.configuration, dispatcher)
    }

    /**
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    fun realmChanged(): Flow<Pair<NativePointer, VersionId>> {
        // FIXME Workaround until proper Realm Changed Listeners are implemented
        return _realmChanged.asSharedFlow()
            .map {
                Pair(it.dbPointer, VersionId(RealmInterop.realm_get_version_id(it.dbPointer)))
            }.distinctUntilChanged { old, new ->
                old.second == new.second
            }
//        return callbackFlow {
//            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
//            withContext(dispatcher) {
//                token.value = addRealmChangedListener { frozenRealm ->
//                    val result = trySend(frozenRealm)
//                    if (!result.isSuccess) {
//                        // FIXME What to do if this fails?
//                        throw IllegalStateException("Notification could not be handled: $result")
//                    }
//                }
//            }
//            awaitClose {
//                token.value.cancel()
//            }
//        }
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
            throw IllegalStateException("Notification could not be sent: $result")
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
                    // FIXME How to make sure the Realm isn't closed when handling this?

                    // FIXME The Realm should have been frozen in `realmChanged`, but this isn't supported yet.
                    //  Instead we create the frozen version ourselves (which is correct, but pretty inefficient)
                    //  We also send it to the owner Realm, so it can keep track of its lifecycle
                    val frozenRealm = RealmReference(owner, RealmInterop.realm_freeze(realm.realmReference.dbPointer))
                    notifyRealmChanged(frozenRealm)

                    // Notifications need to be delivered with the version they where created on, otherwise
                    // the fine-grained notification data might be out of sync.
                    val frozenResults = liveResults.freeze(frozenRealm)
                    callback.onChange(frozenResults)
                }
            }
        )
        return NotificationToken(callback, token)
    }

    private fun notifyRealmChanged(frozenRealm: RealmReference) {
        if (!_realmChanged.tryEmit(frozenRealm)) {
            println("Failed to send update to Realm from the Notifier: ${owner.configuration.path}")
//            throw IllegalStateException("Failed to send update to Realm from the Notifier: ${owner./**/configuration.path}")
        }
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
                    // FIXME How to make sure the Realm isn't closed when handling this?

                    // FIXME The Realm should have been frozen in `realmChanged`, but this isn't supported yet.
                    //  Instead we create the frozen version ourselves (which is correct, but pretty inefficient)
                    val frozenRealm = RealmReference(owner, RealmInterop.realm_freeze(realm.realmReference.dbPointer))
                    notifyRealmChanged(frozenRealm)

                    if (!liveObject.isValid()) {
                        callback.onChange(null)
                    } else {
                        callback.onChange(liveObject.freeze(frozenRealm))
                    }
                }
            }
        )
        return NotificationToken(callback, token)
    }

    /**
     * Listen to changes to a RealmList through a [Flow]. If the list is deleted, `null` is emitted and the flow will complete.
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
        // FIXME Is it safe at all times to close a Realm? Probably not during a changelistener callback, but Mutexes
        //  are not supported within change listeners as they are not suspendable.
        realm.close()
    }
}
