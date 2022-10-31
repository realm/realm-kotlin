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
import io.realm.kotlin.exceptions.RealmException
import io.realm.kotlin.internal.dynamic.DynamicRealmImpl
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmPointer
import io.realm.kotlin.internal.platform.fileExists
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.internal.schema.SchemaMetadata
import io.realm.kotlin.internal.util.DispatcherHolder
import io.realm.kotlin.notifications.RealmChange
import io.realm.kotlin.notifications.internal.InitialRealmImpl
import io.realm.kotlin.notifications.internal.UpdatedRealmImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.takeWhile
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

    public val notificationDispatcherHolder: DispatcherHolder = configuration.notificationDispatcherFactory.create()
    public val writeDispatcherHolder: DispatcherHolder = configuration.writeDispatcherFactory.create()

    internal val realmScope =
        CoroutineScope(SupervisorJob() + notificationDispatcherHolder.dispatcher)
    private val realmFlow =
        MutableSharedFlow<RealmChange<Realm>>() // Realm notifications emit their initial state when subscribed to
    private val notifier =
        SuspendableNotifier(this, notificationDispatcherHolder.dispatcher)
    internal val writer =
        SuspendableWriter(this, writeDispatcherHolder.dispatcher)

    // inline classes cannot be lateinit, so use a placeholder instead.
    private var _realmReference: AtomicRef<RealmReference> = atomic(object : RealmReference {
        override val owner: BaseRealmImpl
            get() = throw IllegalStateException("Placeholder should not be access")
        override val schemaMetadata: SchemaMetadata
            get() = throw IllegalStateException("Placeholder should not be access")
        override val dbPointer: RealmPointer
            get() = throw IllegalStateException("Placeholder should not be access")
    })

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
                // TODO Should actually be a frozen pointer, but since we cannot directly obtain one we expect
                //  a live reference and grab the frozen version of that.
                val (dbPointer, fileCreated) = configuration.openRealm(this@RealmImpl)
                realmFileCreated = fileCreated
                _realmReference = atomic(LiveRealmReference(this@RealmImpl, dbPointer))
                realmReference = _realmReference.value
                // TODO Find a cleaner way to get the initial frozen instance. Currently we expect the
                //  primary constructor supplied dbPointer to be a pointer to a live realm, so get the
                //  frozen pointer and close the live one.
                val frozenReference = (realmReference as LiveRealmReference).snapshot(this@RealmImpl)
                versionTracker.trackAndCloseExpiredReferences(frozenReference)
                realmReference.close()
                realmReference = frozenReference
                configuration.initializeRealmData(this@RealmImpl, fileCreated)
            }

            // Update the Realm if another process or the Sync Client updates the Realm
            realmScope.launch {
                notifier.realmChanged().collect { realmReference ->
                    updateRealmPointer(realmReference)
                }
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
        val realmRef: FrozenRealmReference = notifier.refresh()
        updateRealmPointer(realmRef)
    }

    // Required as Kotlin otherwise gets confused about the visibility and reports
    // "Cannot infer visibility for '...'. Please specify it explicitly"
    override fun <T : BaseRealmObject> query(
        clazz: KClass<T>,
        query: String,
        vararg args: Any?
    ): RealmQuery<T> {
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
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not execute the write transaction"
            )
        }
    }

    override fun <R> writeBlocking(block: MutableRealm.() -> R): R {
        writer.checkInTransaction("Cannot initiate transaction when already in a write transaction")
        return runBlocking {
            write(block)
        }
    }

    override fun asFlow(): Flow<RealmChange<Realm>> {
        return flowOf(
            flow { emit(InitialRealmImpl(this@RealmImpl)) },
            realmFlow.asSharedFlow().takeWhile { !isClosed() }
        ).flattenConcat()
    }

    override fun writeCopyTo(configuration: Configuration) {
        if (fileExists(configuration.path)) {
            throw IllegalArgumentException("File already exists at: ${configuration.path}. Realm can only write a copy to an empty path.")
        }
        val internalConfig = (configuration as InternalConfiguration)
        if (internalConfig.isFlexibleSyncConfiguration) {
            throw IllegalArgumentException("Creating a copy of a Realm where the target has Flexible Sync enabled is currently not supported.")
        }
        val configPtr = internalConfig.createNativeConfiguration()
        try {
            RealmInterop.realm_convert_with_config(
                realmReference.dbPointer,
                configPtr,
                false // We don't want to expose 'merge_with_existing' all the way to the SDK - see docs in the C-API
            )
        } catch (ex: RealmException) {
            if (ex.message?.contains("Could not write file as not all client changes are integrated in server") == true) {
                throw IllegalStateException(ex.message)
            } else {
                throw ex
            }
        }
    }

    override fun <T, C> registerObserver(t: Thawable<Observable<T, C>>): Flow<C> {
        return notifier.registerObserver(t)
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
            realmFlow.emit(UpdatedRealmImpl(this))
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
                realmFlow.emit(UpdatedRealmImpl(this@RealmImpl))
            }
        }
        notificationDispatcherHolder.close()
        writeDispatcherHolder.close()
    }

    internal companion object {
        internal fun create(configuration: InternalConfiguration): RealmImpl {
            try {
                return RealmImpl(configuration)
            } catch (exception: Throwable) {
                throw CoreExceptionConverter.convertToPublicException(
                    exception,
                    "Could not open Realm with the given configuration: ${configuration.debug()}"
                )
            }
        }
    }
}

// Returns a DynamicRealm of the current version of the Realm. Only used to be able to test the
// DynamicRealm API outside of a migration.
internal fun Realm.asDynamicRealm(): DynamicRealm {
    // The RealmImpl.realmReference should be a FrozenRealmReference, but since we cannot
    // initialize it as such we need to cast it here
    val dbPointer = ((this as RealmImpl).realmReference as FrozenRealmReference).dbPointer
    return DynamicRealmImpl(this@asDynamicRealm.configuration as InternalConfiguration, dbPointer)
}
