package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.WeakReference
import io.realm.internal.platform.runBlocking
import io.realm.internal.schema.RealmSchemaImpl
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO API-PUBLIC Document platform specific internals (RealmInitializer, etc.)
internal class RealmImpl private constructor(
    configuration: InternalConfiguration,
    dbPointer: NativePointer
) : BaseRealmImpl(configuration), Realm {

    private val realmPointerMutex = Mutex()

    internal val realmScope =
        CoroutineScope(SupervisorJob() + configuration.notificationDispatcher)
    private val realmFlow =
        MutableSharedFlow<RealmImpl>(replay = 1) // Realm notifications emit their initial state when subscribed to
    private val notifier =
        SuspendableNotifier(this, configuration.notificationDispatcher)
    internal val writer =
        SuspendableWriter(this, configuration.writeDispatcher)

    private var updatableRealm: AtomicRef<RealmReference> = atomic(LiveRealmReference(this, dbPointer))

    /**
     * The current Realm reference that points to the underlying frozen C++ SharedRealm.
     *
     * NOTE: As this is updated to a new frozen version on notifications about changes in the
     * underlying realm, care should be taken not to spread operations over different references.
     */
    internal override var realmReference: RealmReference
        get() = updatableRealm.value
        set(value) {
            updatableRealm.value = value
        }

    // TODO Bit of an overkill to have this as we are only catching the initial frozen version.
    //  Maybe we could just rely on the notifier to issue the initial frozen version, but that
    //  would require us to sync that up. Didn't address this as we already have a todo on fixing
    //  constructing the initial frozen version in the init block.
    private val versionTracker = VersionTracker(log)

    init {
        // TODO Find a cleaner way to get the initial frozen instance. Currently we expect the
        //  primary constructor supplied dbPointer to be a pointer to a live realm, so get the
        //  frozen pointer and close the live one.
        val initialLiveDbPointer = realmReference.dbPointer
        val frozenRealm = RealmInterop.realm_freeze(initialLiveDbPointer)
        realmReference.close()
        val frozenReference = FrozenRealmReference(this, frozenRealm)
        realmReference = frozenReference
        versionTracker.trackNewAndCloseExpiredReferences(frozenReference)
        // Update the Realm if another process or the Sync Client updates the Realm
        realmScope.launch {
            realmFlow.emit(this@RealmImpl)
            notifier.realmChanged().collect { realmReference ->
                log.debug("Receving notification on update: ${this@RealmImpl}")
                updateRealmPointer(realmReference)
            }
        }
    }

    constructor(configuration: InternalConfiguration) :
        this(
            configuration,
            try {
                RealmInterop.realm_open(configuration.nativeConfig)
            } catch (exception: RealmCoreException) {
                throw genericRealmCoreExceptionHandler(
                    "Could not open Realm with the given configuration: ${configuration.debug()}",
                    exception
                )
            }
        )

    // Currently just for internal-only usage in test, thus API is not polished
    internal suspend fun updateSchema(schema: RealmSchemaImpl) {
        updateRealmPointer(this.writer.updateSchema(schema))
    }

    override suspend fun <R> write(block: MutableRealm.() -> R): R {
        try {
            val (reference, result) = this.writer.write(block)
            // Update the user facing Realm before returning the result.
            // That way, querying the Realm right after the `write` completes will return
            // the written data. Otherwise, we would have to wait for the Notifier thread
            // to detect it and update the user Realm.
            updateRealmPointer(reference)
            return result
        } catch (exception: RealmCoreException) {
            throw genericRealmCoreExceptionHandler(
                "Could not execute the write transaction",
                exception
            )
        }
    }

    override fun <R> writeBlocking(block: MutableRealm.() -> R): R {
        writer.checkInTransaction("Cannot initiate transaction when already in a write transaction")
        return runBlocking {
            write(block)
        }
    }

    override fun observe(): Flow<RealmImpl> {
        return realmFlow.asSharedFlow()
    }

    /**
     * FIXME Hidden until we can add proper support
     */
    internal fun addChangeListener(): Cancellable {
        TODO()
    }

    override fun <T> registerObserver(t: Thawable<T>): Flow<T> {
        return notifier.registerObserver(t)
    }

    internal override fun <T : RealmObject> registerResultsChangeListener(
        results: RealmResultsImpl<T>,
        callback: Callback<RealmResultsImpl<T>>
    ): Cancellable {
        TODO("Not yet implemented")
    }

    internal override fun <T : RealmObject> registerListChangeListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        TODO("Not yet implemented")
    }

    internal override fun <T : RealmObject> registerObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        TODO("Not yet implemented")
    }

    private suspend fun updateRealmPointer(newRealmReference: RealmReference) {
        realmPointerMutex.withLock {
            val newVersion = newRealmReference.version()
            log.debug("Updating Realm version: $this $newRealmReference ${version()} -> $newVersion")
            // If we advance to a newer version then we should keep track of the preceding one,
            // otherwise just track the new one directly.
            val untrackedReference = if (newVersion >= version()) {
                val previousRealmReference = realmReference
                realmReference = newRealmReference
                previousRealmReference
            } else {
                newRealmReference
            }
            // Notify public observers that the Realm changed
            realmFlow.emit(this)
        }
    }

    override fun close() {
        // TODO Reconsider this constraint. We have the primitives to check is we are on the
        //  writer thread and just close the realm in writer.close()
        writer.checkInTransaction("Cannot close the Realm while inside a transaction block")
        runBlocking {
            realmPointerMutex.withLock {
                writer.close()
                realmScope.cancel()
                notifier.close()
                versionTracker.close()
                super.close()
            }
        }
        // TODO There is currently nothing that tears down the dispatcher
    }

    // FIXME Internal method to work around that callback subscription is not freed on GC
    //  <issue ref>
    internal fun unregisterCallbacks() {
        writer.unregisterCallbacks()
        notifier.unregisterCallbacks()
    }
}
