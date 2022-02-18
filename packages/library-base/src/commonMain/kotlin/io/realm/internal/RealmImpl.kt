package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.DynamicRealm
import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.runBlocking
import io.realm.internal.schema.RealmSchemaImpl
import io.realm.query.RealmQuery
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
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitializer, etc.)
internal class RealmImpl private constructor(
    configuration: InternalConfiguration,
    dbPointer: NativePointer
) : BaseRealmImpl(configuration), Realm, TypedRealmImpl {

    private val realmPointerMutex = Mutex()

    internal val realmScope =
        CoroutineScope(SupervisorJob() + configuration.notificationDispatcher)
    private val realmFlow =
        MutableSharedFlow<RealmImpl>(replay = 1) // Realm notifications emit their initial state when subscribed to
    private val notifier =
        SuspendableNotifier(this, configuration.notificationDispatcher)
    internal val writer =
        SuspendableWriter(this, configuration.writeDispatcher)

    private var _realmReference: AtomicRef<RealmReference> = atomic(LiveRealmReference(this, dbPointer))
    /**
     * The current Realm reference that points to the underlying frozen C++ SharedRealm.
     *
     * NOTE: As this is updated to a new frozen version on notifications about changes in the
     * underlying realm, care should be taken not to spread operations over different references.
     */
    // TODO Could just be FrozenRealmReference but fails to close all references if full
    //  initialization is moved to the initialization of updatableRealm ... maybe a caveat with
    //  atomicfu
    override var realmReference: RealmReference by _realmReference

    // TODO Bit of an overkill to have this as we are only catching the initial frozen version.
    //  Maybe we could just rely on the notifier to issue the initial frozen version, but that
    //  would require us to sync that up. Didn't address this as we already have a todo on fixing
    //  constructing the initial frozen version in the initialization of updatableRealm.
    private val versionTracker = VersionTracker(log)

    init {
        // TODO Find a cleaner way to get the initial frozen instance. Currently we expect the
        //  primary constructor supplied dbPointer to be a pointer to a live realm, so get the
        //  frozen pointer and close the live one.
        val frozenReference = (realmReference as LiveRealmReference).snapshot(this)
        versionTracker.trackAndCloseExpiredReferences(frozenReference)
        realmReference.close()
        realmReference = frozenReference
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

    override fun <T : RealmObject> query(clazz: KClass<T>, query: String, vararg args: Any?): RealmQuery<T> {
        return super.query(clazz, query, *args)
    }

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

    private suspend fun updateRealmPointer(newRealmReference: FrozenRealmReference) {
        realmPointerMutex.withLock {
            versionTracker.trackAndCloseExpiredReferences()
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
                // The local realmReference is pointing to a realm reference managed by either the
                // version tracker, writer or notifier, so it is already closed
                super.close()
            }
        }
        // TODO There is currently nothing that tears down the dispatcher
    }

    // FIXME Internal method to work around that callback subscription is not freed on GC
    //  https://github.com/realm/realm-kotlin/issues/671
    internal fun unregisterCallbacks() {
        writer.unregisterCallbacks()
        notifier.unregisterCallbacks()
    }
}

// Returns a DynamicRealm of the current version of the Realm. Only used to be able to test the
// DynamicRealm API outside of migration
internal fun Realm.asDynamicRealm(): DynamicRealm =
    DynamicRealmImpl(this@asDynamicRealm.configuration as InternalConfiguration, (this as RealmImpl).realmReference.dbPointer)
