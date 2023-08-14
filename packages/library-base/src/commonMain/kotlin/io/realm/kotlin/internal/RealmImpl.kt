/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.internal

import io.realm.kotlin.Configuration
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.Realm
import io.realm.kotlin.dynamic.DynamicRealm
import io.realm.kotlin.internal.dynamic.DynamicRealmImpl
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.SynchronizableObject
import io.realm.kotlin.internal.platform.copyAssetFile
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.internal.util.DispatcherHolder
import io.realm.kotlin.internal.util.Validation.sdkError
import io.realm.kotlin.internal.util.terminateWhen
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.notifications.internal.InitialRealmImpl
import io.realm.kotlin.notifications.internal.UpdatedRealmImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.TypedRealmObject
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.reflect.KClass

// TODO API-PUBLIC Document platform specific internals (RealmInitializer, etc.)
// TODO Public due to being accessed from `SyncedRealmContext`
public class RealmImpl private constructor(
    configuration: InternalConfiguration
) : BaseRealmImpl(configuration), Realm, InternalTypedRealm, Flowable<RealmChange<Realm>> {

    private val realmPointerMutex = Mutex()

    public val notificationDispatcherHolder: DispatcherHolder =
        configuration.notificationDispatcherFactory.create()
    public val writeDispatcherHolder: DispatcherHolder =
        configuration.writeDispatcherFactory.create()

    internal val realmScope =
        CoroutineScope(SupervisorJob() + notificationDispatcherHolder.dispatcher)
    private val notifierFlow: MutableSharedFlow<RealmChange<Realm>> =
        MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val notifier =
        SuspendableNotifier(this, notificationDispatcherHolder.dispatcher)
    private val writer =
        SuspendableWriter(this, writeDispatcherHolder.dispatcher)

    // Internal flow to ease monitoring of realm state for closing active flows then the realm is
    // closed.
    internal val realmStateFlow =
        MutableSharedFlow<State>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var _realmReference: AtomicRef<FrozenRealmReference?> = atomic(null)
    private val realmReferenceLock = SynchronizableObject()

    /**
     * The current Realm reference that points to the underlying frozen C++ SharedRealm.
     *
     * NOTE: As this is updated to a new frozen version on notifications about changes in the
     * underlying realm, care should be taken not to spread operations over different references.
     */
    override val realmReference: FrozenRealmReference
        get() = realmReference()

    // TODO Bit of an overkill to have this as we are only catching the initial frozen version.
    //  Maybe we could just rely on the notifier to issue the initial frozen version, but that
    //  would require us to sync that up. Didn't address this as we already have a todo on fixing
    //  constructing the initial frozen version in the initialization of updatableRealm.
    private val versionTracker = VersionTracker(this, log)

    // Injection point for synchronized Realms. This property should only be used to hold state
    // required by synchronized realms. See `SyncedRealmContext` for more details.
    public var syncContext: AtomicRef<Any?> = atomic(null)

    init {
        @Suppress("TooGenericExceptionCaught")
        // Track whether or not the file was created as part of opening the Realm. We need this
        // so we can remove the file again if case opening the Realm fails.
        var realmFileCreated = false
        try {
            runBlocking {
                var assetFileCopied = false
                configuration.initialRealmFileConfiguration?.let {
                    val path = configuration.path
                    if (!fileExists(path)) {
                        // TODO We cannot ensure exclusive access to the realm file, so for now
                        //  just try avoid having multiple threads in the same process copying
                        //  asset files at the same time.
                        //  https://github.com/realm/realm-core/issues/6492
                        assetProcessingLock.withLock {
                            if (!fileExists(path)) {
                                log.debug("Copying asset file: ${it.assetFile}")
                                assetFileCopied = true
                                copyAssetFile(path, it.assetFile, it.checksum)
                            }
                        }
                    }
                }
                val (frozenReference, fileCreated) = configuration.openRealm(this@RealmImpl)
                realmFileCreated = assetFileCopied || fileCreated
                versionTracker.trackAndCloseExpiredReferences(frozenReference)
                _realmReference.value = frozenReference
                configuration.initializeRealmData(this@RealmImpl, realmFileCreated)
            }

            realmScope.launch {
                notifier.realmChanged().collect {
                    notifierFlow.emit(UpdatedRealmImpl(this@RealmImpl))
                }
            }
            if (!realmStateFlow.tryEmit(State.OPEN)) {
                log.warn("Cannot signal internal open")
            }
        } catch (ex: Throwable) {
            // Something went wrong initializing Realm, delete the file, so initialization logic
            // can run again.
            close()
            if (realmFileCreated) {
                try {
                    Realm.deleteRealm(configuration)
                } catch (ex: IllegalStateException) {
                    // Ignore. See https://github.com/realm/realm-kotlin/issues/851
                    // Currently there is no reliable way to delete a synchronized
                    // Realm. So ignore if this fails for now.
                    log.debug(
                        "An error happened while trying to reset the realm after " +
                            "opening it for the first time failed. The realm must be manually " +
                            "deleted if `initialData` and `initialSubscriptions` should run " +
                            "again: $ex"
                    )
                }
            }
            throw ex
        }
    }

    /**
     * Manually force this Realm to update to the latest version.
     * The refresh will also trigger any relevant notifications.
     * TODO Public because it is called from `SyncSessionImpl`.
     */
    public suspend fun refresh() {
        // We manually force a refresh of the notifier Realm and manually update the user
        // facing Realm with the updated version. Note, as the notifier asynchronously also update
        // the user Realm, we cannot guarantee that the Realm has this exact version when
        // this method completes. But we can guarantee that it has _at least_ this version.
        notifier.refresh()
    }

    // Required as Kotlin otherwise gets confused about the visibility and reports
    // "Cannot infer visibility for '...'. Please specify it explicitly"
    override fun <T : TypedRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> {
        return super.query(clazz, query, *args)
    }

    // Currently just for internal-only usage in test, thus API is not polished
    internal suspend fun updateSchema(schema: RealmSchemaImpl) {
        this.writer.updateSchema(schema)
    }

    override suspend fun <R> write(block: MutableRealm.() -> R): R = writer.write(block)

    override fun <R> writeBlocking(block: MutableRealm.() -> R): R {
        writer.checkInTransaction("Cannot initiate transaction when already in a write transaction")
        return runBlocking {
            write(block)
        }
    }

    override fun asFlow(): Flow<RealmChange<Realm>> = scopedFlow {
        notifierFlow.onStart { emit(InitialRealmImpl(this@RealmImpl)) }
    }

    override fun writeCopyTo(configuration: Configuration) {
        if (fileExists(configuration.path)) {
            throw IllegalArgumentException("File already exists at: ${configuration.path}. Realm can only write a copy to an empty path.")
        }
        val internalConfig = (configuration as InternalConfiguration)
        val configPtr = internalConfig.createNativeConfiguration()

        RealmInterop.realm_convert_with_config(
            realmReference.dbPointer,
            configPtr,
            false // We don't want to expose 'merge_with_existing' all the way to the SDK - see docs in the C-API
        )
    }

    override fun <T : CoreNotifiable<T, C>, C> registerObserver(t: Observable<T, C>, keyPaths: Array<out String>): Flow<C> {
        return notifier.registerObserver(t, keyPaths)
    }

    public fun realmReference(): FrozenRealmReference {
        realmReferenceLock.withLock {
            val value1 = _realmReference.value
            // We don't consider advancing the version if is is already closed.
            value1?.let {
                if (it.isClosed()) return it
            }

            // Consider versions of current realm, notifier and writer to identify if we should
            // advance the user facing realms version to a newer frozen snapshot.
            val version = value1?.version()
            val notifierSnapshot = notifier.version
            val writerSnapshot = writer.version

            var newest: LiveRealmHolder<LiveRealm>? = null
            if (notifierSnapshot != null && version != null && notifierSnapshot > version) {
                newest = notifier
            }
            @Suppress("ComplexCondition")
            if (writerSnapshot != null && version != null && ((writerSnapshot > version) || (notifierSnapshot != null && writerSnapshot > notifierSnapshot))) {
                newest = writer
            }
            if (newest != null) {
                _realmReference.value = newest.snapshot
                log.debug("$this ADVANCING $version -> ${_realmReference.value?.version()}")
            }
        }
        return _realmReference.value ?: sdkError("Accessing realmReference before realm has been opened")
    }

    public fun activeVersions(): VersionInfo {
        val mainVersions: VersionData? = _realmReference.value?.let { VersionData(it.uncheckedVersion(), versionTracker.versions()) }
        return VersionInfo(mainVersions, notifier.versions(), writer.versions())
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
        if (!realmStateFlow.tryEmit(State.CLOSED)) {
            log.warn("Cannot signal internal close")
        }
        notificationDispatcherHolder.close()
        writeDispatcherHolder.close()
    }

    internal companion object {
        // Mutex to ensure that only one thread is trying to copy asset files in place at a time.
        //  https://github.com/realm/realm-core/issues/6492
        private val assetProcessingLock = SynchronizableObject()

        internal fun create(configuration: InternalConfiguration): RealmImpl {
            return RealmImpl(configuration)
        }
    }

    /**
     * Internal state to be able to make a [State] flow that we can easily monitor and use to close
     * flows within a coroutine context.
     */
    internal enum class State { OPEN, CLOSED, }

    /**
     * Flow wrapper that will complete the flow returned by [block] when the realm is closed.
     */
    internal inline fun <T> scopedFlow(crossinline block: () -> Flow<T>): Flow<T> {
        return block().terminateWhen(realmStateFlow) { state -> state == State.CLOSED }
    }
}

// Returns a DynamicRealm of the current version of the Realm. Only used to be able to test the
// DynamicRealm API outside of a migration.
internal fun Realm.asDynamicRealm(): DynamicRealm {
    val dbPointer = (this as RealmImpl).realmReference.dbPointer
    return DynamicRealmImpl(this@asDynamicRealm.configuration, dbPointer)
}
