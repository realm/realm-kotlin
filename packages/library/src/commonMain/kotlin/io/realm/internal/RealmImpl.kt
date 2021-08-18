package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
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
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitializer, etc.)
internal class RealmImpl private constructor(configuration: RealmConfigurationImpl, dbPointer: NativePointer) :
    BaseRealmImpl(configuration, dbPointer), Realm {

    internal val realmScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + configuration.notificationDispatcher)
    private val realmFlow =
        MutableSharedFlow<RealmImpl>(replay = 1) // Realm notifications emit their initial state when subscribed to
    private val notifier = SuspendableNotifier(this, configuration.notificationDispatcher)
    private val writer = SuspendableWriter(this, configuration.writeDispatcher)
    private val realmPointerMutex = Mutex()

    private var updatableRealm: AtomicRef<RealmReference> = atomic(RealmReference(this, dbPointer))

    /**
     * The current Realm reference that points to the underlying frozen C++ SharedRealm.
     *
     * NOTE: As this is updated to a new frozen version on notifications about changes in the
     * underlying realm, care should be taken not to spread operations over different references.
     */
    internal override var realmReference: RealmReference
        get() {
            return updatableRealm.value
        }
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

    /**
     * Open a Realm instance. This instance grants access to an underlying Realm file defined by
     * the provided [RealmConfiguration].
     *
     * @param configuration The RealmConfiguration used to open the Realm.
     */
    // FIXME Figure out how to describe the constructor better
    public constructor(configuration: RealmConfiguration) :
        this(configuration as RealmConfigurationImpl, RealmInterop.realm_open(configuration.nativeConfig))

    override fun <T : RealmObject> objects(clazz: KClass<T>): RealmResults<T> {
        return super.objects(clazz)
    }

    override suspend fun <R> write(block: MutableRealm.() -> R): R {
        @Suppress("TooGenericExceptionCaught") // FIXME https://github.com/realm/realm-kotlin/issues/70
        try {
            val (nativePointer, versionId, result) = this.writer.write(block)
            // Update the user facing Realm before returning the result.
            // That way, querying the Realm right after the `write` completes will return
            // the written data. Otherwise, we would have to wait for the Notifier thread
            // to detect it and update the user Realm.
            updateRealmPointer(RealmReference(this, nativePointer))
            return result
        } catch (e: Exception) {
            throw e
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

    internal override fun <T : RealmObject> registerResultsObserver(results: RealmResultsImpl<T>): Flow<RealmResultsImpl<T>> {
        return notifier.resultsChanged(results)
    }

    internal override fun <T : RealmObject> registerListObserver(list: List<T>): Flow<List<T>> {
        return notifier.listChanged(list)
    }

    internal override fun <T : RealmObject> registerObjectObserver(obj: T): Flow<T> {
        return notifier.objectChanged(obj)
    }

    internal override fun <T : RealmObject> registerResultsChangeListener(
        results: RealmResultsImpl<T>,
        callback: Callback<RealmResultsImpl<T>>
    ): Cancellable {
        return notifier.registerResultsChangedListener(results, callback)
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
