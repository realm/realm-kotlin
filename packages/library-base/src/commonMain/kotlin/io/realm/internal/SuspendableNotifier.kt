package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.VersionId
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.runBlocking
import io.realm.internal.util.freezeOnOldMM
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
    private val owner: RealmImpl,
    private val dispatcher: CoroutineDispatcher
) {

    companion object {
        val NO_OP_NOTIFICATION_TOKEN = object : Cancellable {
            override fun cancel() { /* Do Nothing */
            }
        }
    }

    // FIXME Work-around for the global Realm changed listener not working.
    // Adding extra buffer capacity as we are otherwise never able to emit anything
    // see https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt#L78
    private val _realmChanged = MutableSharedFlow<RealmReference>(
        onBufferOverflow = BufferOverflow.SUSPEND,
        extraBufferCapacity = 1
    )

    private val realmInitializer = lazy {
        val dbPointer = RealmInterop.realm_open((owner.configuration as InternalConfiguration).nativeConfig, dispatcher)
        object : BaseRealmImpl(owner.configuration, dbPointer) {
            /* Realms used by the Notifier is just a basic Live Realm */
        }
    }
    // Must only be accessed from the dispatchers thread
    private val realm: BaseRealmImpl by realmInitializer

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
        // https://github.com/realm/realm-core/issues/4613
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

    internal fun <T> registerObserver(thawable: Thawable<T>): Flow<T> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> = kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                val liveRef: Notifiable<T> = thawable.thaw(realm.realmReference)
                    ?: error("Cannot listen for changes on a deleted reference")
                val interopCallback: io.realm.internal.interop.Callback =
                    object : io.realm.internal.interop.Callback {
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
                            // Notifications need to be delivered with the version they where created on, otherwise
                            // the fine-grained notification data might be out of sync.
                            liveRef.emitFrozenUpdate(frozenRealm, change, this@callbackFlow)
                                ?.let { checkResult(it) }
                        }
                    }.freezeOnOldMM()
                val newToken =
                    NotificationToken<Callback<T>>(
                        // FIXME What is this callback for anyway?
                        callback = Callback { },
                        token = liveRef.registerForNotification(interopCallback)
                    )
                token.value = newToken
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

    internal fun close() {
        // FIXME Is it safe at all times to close a Realm? Probably not during a changelistener callback, but Mutexes
        //  are not supported within change listeners as they are not suspendable.
        runBlocking(dispatcher) {
            if (realmInitializer.isInitialized()) {
                // Calling close on a non initialized Realm is wasteful since before calling RealmInterop.close
                // The Realm will be first opened (RealmInterop.open) and an instance created in vain.
                realm.close()
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

    private fun notifyRealmChanged(frozenRealm: RealmReference) {
        if (!_realmChanged.tryEmit(frozenRealm)) {
            // FIXME Figure out why we sometimes end up here
            println("Failed to send update to Realm from the Notifier: ${owner./**/configuration.path}")
            // throw IllegalStateException("Failed to send update to Realm from the Notifier: ${owner./**/configuration.path}")
        }
    }
}
