package io.realm.kotlin.internal

import io.realm.kotlin.VersionId
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.internal.util.trySendWithBufferOverflowCheck
import io.realm.kotlin.notifications.internal.Cancellable
import io.realm.kotlin.notifications.internal.Cancellable.Companion.NO_OP_NOTIFICATION_TOKEN
import io.realm.kotlin.schema.RealmSchema
import kotlinx.atomicfu.AtomicRef
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.ProducerScope
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
    // Adding extra buffer capacity as we are otherwise never able to emit anything
    // see https://github.com/Kotlin/kotlinx.coroutines/blob/master/kotlinx-coroutines-core/common/src/flow/SharedFlow.kt#L78
    private val _realmChanged = MutableSharedFlow<FrozenRealmReference>(
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
        extraBufferCapacity = 1
    )

    // Could just be anonymous class, but easiest way to get BaseRealmImpl.toString to display the
    // right type with this
    private inner class NotifierRealm : LiveRealm(owner, owner.configuration, dispatcher) {
        // This is guaranteed to be triggered before any other notifications for the same
        // update as we get all callbacks on the same single thread dispatcher
        override fun onRealmChanged() {
            super.onRealmChanged()
            if (!_realmChanged.tryEmit(this.snapshot)) {
                // Should never fail to emit snapshot version as we just drop oldest
                sdkError("Failed to emit snapshot version")
            }
        }

        // FIXME Currently constructs a new instance on each invocation. We could cache this pr. schema
        //  update, but requires that we initialize it all on the actual schema update to allow freezing
        //  it. If we make the schema backed by the actual realm_class_info_t/realm_property_info_t
        //  initialization it would probably be acceptable to initialize on schema updates
        override fun schema(): RealmSchema {
            return RealmSchemaImpl.fromTypedRealm(realmReference.dbPointer, realmReference.schemaMetadata)
        }
    }

    private val realmInitializer = lazy { NotifierRealm() }
    // Must only be accessed from the dispatchers thread
    private val realm: NotifierRealm by realmInitializer

    /**
     * Listen to changes to a Realm.
     *
     * This flow is guaranteed to emit before any other streams listening to individual objects or
     * query results.
     */
    internal suspend fun realmChanged(): Flow<FrozenRealmReference> {
        // Touching realm will open the underlying realm and register change listeners, but must
        // happen on the dispatcher as the realm can only be touched on the dispatcher's thread.
        withContext(dispatcher) { realm }
        return _realmChanged.asSharedFlow()
    }

    internal fun <T : CoreObservable<T, C>, C> registerObserver(flowable: NotificationFlowable<T, C>): Flow<C> {
        return callbackFlow {
            val token: AtomicRef<Cancellable> =
                kotlinx.atomicfu.atomic(NO_OP_NOTIFICATION_TOKEN)
            withContext(dispatcher) {
                ensureActive()
                // Ensure that the live realm is always up to date to avoid registering
                // notifications on newer objects.
                realm.refresh()
                val observable = flowable.observable()
                val lifeRef = observable.coreObservable(realm)
                val changeBuilder = observable.changeBuilder()
                if (lifeRef != null) {
                    val interopCallback: Callback<RealmChangesPointer> =
                        object : Callback<RealmChangesPointer> {
                            override fun onChange(change: RealmChangesPointer) {
                                // FIXME How to make sure the Realm isn't closed when handling this?
                                // Notifications need to be delivered with the version they where created on, otherwise
                                // the fine-grained notification data might be out of sync.
                                val frozenObservable = lifeRef.freeze(realm.snapshot)
                                emit(changeBuilder.change(frozenObservable, change))
                            }
                        }
                    token.value = NotificationToken(lifeRef.registerForNotification(interopCallback))
                }
                // Initial event
                emit(changeBuilder.change(lifeRef?.freeze(realm.snapshot)))
            }
            awaitClose {
                token.value.cancel()
            }
        }
    }

    internal fun close() {
        // FIXME Is it safe at all times to close a Realm? Probably not during a changelistener callback, but Mutexes
        //  are not supported within change listeners as they are not suspendable.
        runBlocking(dispatcher) {
            // Calling close on a non initialized Realm is wasteful since before calling RealmInterop.close
            // The Realm will be first opened (RealmInterop.open) and an instance created in vain.
            if (realmInitializer.isInitialized()) {
                realm.close()
            }
        }
    }

    /**
     * Manually force a refresh of the Realm, moving it to the latest version.
     * This will also trigger the evaluation of all change listeners, which will
     * be triggered as normal if anything changed.
     *
     * @return a frozen reference to the version of the Realm after the refresh.
     */
    suspend fun refresh(): FrozenRealmReference {
        return withContext(dispatcher) {
            // This logic should be safe due to the following reasons:
            // - Notifications and `refresh()` run on the same single-threaded dispatcher.
            // - `refresh()` will synchronously run notifications if the Realm is advanced.
            // - This mean that the `realm.snapshot` will have been updated synchronously
            //   through `onRealmChanged()` when `realm_refresh` completes.
            // - Thus we are guaranteed that `realm.snapshot` contains exactly the version
            //   the live Realm was advanced to when refreshing.
            val dbPointer = realm.realmReference.dbPointer
            RealmInterop.realm_refresh(dbPointer)
            val refreshedVersion = VersionId(RealmInterop.realm_get_version_id(dbPointer))
            realm.snapshot.also { snapshot ->
                // Assert that the above invariants never break
                val snapshotVersion = snapshot.version()
                if (snapshotVersion != refreshedVersion) {
                    throw IllegalStateException(
                        """
                        Live Realm and Snapshot version does not 
                        match: $refreshedVersion vs. $snapshotVersion
                        """.trimIndent()
                    )
                }
            }
        }
    }
}

private fun <T> ProducerScope<T>.emit(p: Pair<T?, Boolean>) {
    val (element, shouldClose) = p
    element?.let {
        trySendWithBufferOverflowCheck(it)
    }
    if (shouldClose) {
        close()
    }
}
