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
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO API-PUBLIC Document platform specific internals (RealmInitializer, etc.)
internal class RealmImpl private constructor(
    configuration: InternalConfiguration,
    dbPointer: NativePointer
) : BaseRealmImpl(configuration, dbPointer), Realm {

    private val realmPointerMutex = Mutex()

    internal val realmScope =
        CoroutineScope(SupervisorJob() + configuration.notificationDispatcher)
    private val realmFlow =
        MutableSharedFlow<RealmImpl>(replay = 1) // Realm notifications emit their initial state when subscribed to
    private val notifier =
        SuspendableNotifier(this, configuration.notificationDispatcher)
    private val writer =
        SuspendableWriter(this, configuration.writeDispatcher)

    private var updatableRealm: AtomicRef<RealmReference> = atomic(RealmReference(this, dbPointer))

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

    // Set of currently open realms. Storing the native pointer explicitly to enable us to close
    // the realm when the RealmReference is no longer referenced anymore.
    internal val intermediateReferences: AtomicRef<Set<Pair<NativePointer, WeakReference<RealmReference>>>> =
        atomic(mutableSetOf())

    init {
        // TODO Find a cleaner way to get the initial frozen instance. Currently we expect the
        //  primary constructor supplied dbPointer to be a pointer to a live realm, so get the
        //  frozen pointer and close the live one.
        val initialLiveDbPointer = realmReference.dbPointer
        val frozenRealm = RealmInterop.realm_freeze(initialLiveDbPointer)
        RealmInterop.realm_close(initialLiveDbPointer)
        realmReference = RealmReference(this, frozenRealm)
        // Update the Realm if another process or the Sync Client updates the Realm
        realmScope.launch {
            realmFlow.emit(this@RealmImpl)
            notifier.realmChanged().collect { realmReference ->
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
            log.debug("Updating Realm version: ${version()} -> $newVersion")
            // If we advance to a newer version then we should keep track of the preceding one,
            // otherwise just track the new one directly.
            val untrackedReference = if (newVersion >= version()) {
                val previousRealmReference = realmReference
                realmReference = newRealmReference
                previousRealmReference
            } else {
                newRealmReference
            }
            trackNewAndCloseExpiredReferences(untrackedReference)

            // Notify public observers that the Realm changed
            realmFlow.emit(this)
        }
    }

    // Must only be called with realmPointerMutex locked
    private fun trackNewAndCloseExpiredReferences(realmReference: RealmReference) {
        val references = mutableSetOf<Pair<NativePointer, WeakReference<RealmReference>>>(
            Pair(realmReference.dbPointer, WeakReference(realmReference))
        )
        intermediateReferences.value.forEach { entry ->
            val (pointer, ref) = entry
            if (ref.get() == null) {
                log.debug("Closing unreferenced version: ${RealmInterop.realm_get_version_id(pointer)}")
                RealmInterop.realm_close(pointer)
            } else {
                references.add(entry)
            }
        }
        intermediateReferences.value = references
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
                super.close()
                intermediateReferences.value.forEach { (pointer, _) ->
                    log.debug(
                        "Closing intermediated version: ${RealmInterop.realm_get_version_id(pointer)}"
                    )
                    RealmInterop.realm_close(pointer)
                }
            }
        }
        // TODO There is currently nothing that tears down the dispatcher
    }
}
