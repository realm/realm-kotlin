package io.realm.internal

import io.realm.BaseRealm
import io.realm.notifications.Callback
import io.realm.notifications.Cancellable
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.VersionId
import io.realm.internal.platform.freeze
import io.realm.internal.platform.runBlocking
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
import kotlinx.coroutines.withContext

/**
 * Class responsible for controlling notifications for a Realm. It does this by wrapping a live Realm on which
 * notifications can be registered. Since all objects that are otherwise exposed to users are frozen, they need
 * to be thawed when reaching the live Realm.
 *
 * For Lists and Objects, this can result in the object no longer existing. In this case, Flows will just complete.
 * End users can catch this case by using `flow.onCompletion { ... }`.
 *
 * Users are only exposed to live objects inside a [MutableRealm], and change listeners are not supported
 * inside writes. Users can therefor not register change listeners on live objects, but it is assumed that other
 * layers check that invariant before methods on this class are called.
 */
internal class SuspendableNotifier(
    private val owner: Realm,
    private val dispatcher: CoroutineDispatcher
) {

    companion object {
        val NO_OP_NOTIFICATION_TOKEN = object : Cancellable {
            override fun cancel() { /* Do Nothing */ }
        }
    }

    // FIXME Work-around for the global Realm changed listener not working.
    // Adding extra buffer capacity as we are otherwise never able to emit anything
    // see https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt#L78
    private val _realmChanged = MutableSharedFlow<RealmReference>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 1
    )

    // Must only be accessed from the dispatchers thread
    private val realm: BaseRealm by lazy {
        val dbPointer = RealmInterop.realm_open(owner.configuration.nativeConfig, dispatcher)
        object : BaseRealm(owner.configuration, dbPointer) {
            /* Realms used by the Notifier is just a basic Live Realm */
        }
    }

    /**
     * FIXME Currently this is a hacked implementation that only does the correct thing if
     *  other RealmResults or RealmObjects are being observed. But all writes should also flow
     *  from [SuspendableWriter], so no Realm updates will be lost to end users.
     *
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    internal fun realmChanged(): Flow<RealmReference> {
        // FIXME Workaround until proper Realm Changed Listeners are implemented
        return _realmChanged.asSharedFlow()
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

    /**
     * Listen to changes to a RealmResults.
     */
    internal fun <T : RealmObject> resultsChanged(results: RealmResults<T>): Flow<RealmResults<T>> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                val newToken = registerResultsChangedListener(results) { frozenResults ->
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

    /**
     * Listen to changes to a RealmObject through a [Flow]. If the object is deleted, null is emitted and the flow will complete.
     */
    internal fun <T : RealmObject> objectChanged(obj: T): Flow<T> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                token.value = registerObjectChangedListener(obj) { frozenObj ->
                    if (frozenObj == null) {
                        close()
                    } else {
                        val result = trySend(frozenObj)
                        checkResult(result)
                    }
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    /**
     * Listen to changes to a RealmList through a [Flow].
     */
    internal fun <T> listChanged(list: RealmList<T>): Flow<RealmList<T>> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                token.value = registerListChangedListener(list) { frozenList ->
                    if (frozenList == null) {
                        close()
                    } else {
                        // Realm should already have been updated with the latest version
                        // So `owner` should as a minimum be at the same version as the notification Realm.
                        val result = trySend(frozenList)
                        checkResult(result)
                    }
                }
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    /**
     * Listen to changes to the Realm.
     * The callback will happen on the configured [SuspendableNotifier.dispatcher] thread.     *
     *
     * FIXME Callers of this method must make sure it is called on the correct [SuspendableNotifier.dispatcher].
     */
    internal fun registerRealmChangedListener(callback: Callback<Pair<NativePointer, VersionId>>): Cancellable {
        TODO("Waiting for RealmInterop to have support for global Realm changed")
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Register a change listener on a live RealmResults. All objects returned in the callback are frozen.
     * The callback will happen on the configured [SuspendableNotifier.dispatcher] thread.     *
     *
     * FIXME Callers of this method must make sure it is called on the correct [SuspendableNotifier.dispatcher].
     */
    internal fun <T : RealmObject> registerResultsChangedListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        val liveResults = results.thaw(realm.realmReference)
        return registerChangedListener(
            liveComponentPointer = liveResults.result,
            notifyComponentUpdate = { frozenRealm ->
                // Notifications need to be delivered with the version they where created on, otherwise
                // the fine-grained notification data might be out of sync.
                val frozenResults = liveResults.freeze(frozenRealm)
                callback.onChange(frozenResults)
            },
            getToken = { resultsPtr, interopCallback ->
                RealmInterop.realm_results_add_notification_callback(resultsPtr, interopCallback)
            },
            callback = callback
        )
    }

    // FIXME Need to expose change details to the user
    //  https://github.com/realm/realm-kotlin/issues/115
    /**
     * Listen to changes to a RealmObject through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     *
     * FIXME Callers of this method must make sure it is called on the correct [SuspendableNotifier.dispatcher].
     */
    internal fun <T : RealmObject> registerObjectChangedListener(
        obj: T,
        callback: Callback<T?>
    ): Cancellable {
        val liveObject: RealmObjectInternal? =
            (obj as RealmObjectInternal).thaw(realm.realmReference.owner) as RealmObjectInternal?
        if (liveObject == null || !liveObject.isValid()) {
            return NO_OP_NOTIFICATION_TOKEN
        }
        return registerChangedListener(
            liveComponentPointer = liveObject.`$realm$ObjectPointer`!!,
            notifyComponentUpdate = { frozenRealm ->
                if (!liveObject.isValid()) {
                    callback.onChange(null)
                } else {
                    callback.onChange(liveObject.freeze(frozenRealm))
                }
            },
            getToken = { objPtr, interopCallback ->
                RealmInterop.realm_object_add_notification_callback(objPtr, interopCallback)
            },
            callback = callback
        )
    }

    /**
     * Listen to changes to a RealmList through a change listener. The callback will happen
     * on the configured [SuspendableNotifier.dispatcher] thread.
     *
     * FIXME Callers of this method must make sure it is called on the correct [SuspendableNotifier.dispatcher].
     */
    internal fun <T> registerListChangedListener(
        list: RealmList<T>,
        callback: Callback<RealmList<T>?>
    ): Cancellable {
        val liveList = list.thaw(realm.realmReference)
        return registerChangedListener(
            liveComponentPointer = liveList.listPtr,
            notifyComponentUpdate = { frozenRealm ->
                if (!liveList.isValid()) {
                    callback.onChange(null)
                } else {
                    // Notifications need to be delivered with the version they where created on, otherwise
                    // the fine-grained notification data might be out of sync.
                    val frozenList = liveList.freeze(frozenRealm)
                    callback.onChange(frozenList)
                }
            },
            getToken = { listPtr, interopCallback ->
                RealmInterop.realm_list_add_notification_callback(listPtr, interopCallback)
            },
            callback = callback
        )
    }

    internal fun close() {
        // FIXME Is it safe at all times to close a Realm? Probably not during a changelistener callback, but Mutexes
        //  are not supported within change listeners as they are not suspendable.
        runBlocking(dispatcher) {
            realm.close()
        }
    }

    private fun <T> registerChangedListener(
        liveComponentPointer: NativePointer,
        notifyComponentUpdate: (frozenRealm: RealmReference) -> Unit,
        getToken: (
            liveComponentPointer: NativePointer,
            interopCallback: io.realm.interop.Callback
        ) -> NativePointer,
        callback: Callback<T>
    ): NotificationToken<Callback<T>> {
        val interopCallback = object : io.realm.interop.Callback {
            override fun onChange(change: NativePointer) {
                // FIXME How to make sure the Realm isn't closed when handling this?

                // FIXME The Realm should have been frozen in `realmChanged`, but this isn't supported yet.
                //  Instead we create the frozen version ourselves (which is correct, but pretty inefficient)
                //  We also send it to the owner Realm, so it can keep track of its lifecycle
                val frozenRealm = RealmReference(
                    owner,
                    RealmInterop.realm_freeze(realm.realmReference.dbPointer)
                )
                notifyRealmChanged(frozenRealm)
                notifyComponentUpdate(frozenRealm)
            }
        }.freeze() // Freeze to allow cleaning up on another thread
        val token = getToken(liveComponentPointer, interopCallback)
        return NotificationToken(callback, token)
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

    private fun notifyRealmChanged(frozenRealm: RealmReference) {
        if (!_realmChanged.tryEmit(frozenRealm)) {
            // FIXME Figure out why we sometimes end up here
            println("Failed to send update to Realm from the Notifier: ${owner./**/configuration.path}")
            // throw IllegalStateException("Failed to send update to Realm from the Notifier: ${owner./**/configuration.path}")
        }
    }
}
