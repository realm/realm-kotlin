/*
 * Copyright 2020 Realm Inc.
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
package io.realm

import io.realm.internal.FlowNotificationToken
import io.realm.internal.RealmReference
import io.realm.internal.SuspendableNotifier
import io.realm.internal.SuspendableWriter
import io.realm.internal.WeakReference
import io.realm.internal.runBlocking
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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm private constructor(configuration: RealmConfiguration, dbPointer: NativePointer) :
    BaseRealm(configuration, dbPointer) {

    internal val realmScope: CoroutineScope = CoroutineScope(SupervisorJob() + configuration.notificationDispatcher)
    private val realmFlow = MutableSharedFlow<Realm>(replay = 1) // Realm notifications emit their initial state when subscribed to
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

    companion object {
        /**
         * Default name for Realm files unless overridden by [RealmConfiguration.Builder.name].
         */
        public const val DEFAULT_FILE_NAME = "default.realm"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG = "REALM"

        /**
         * Open a realm.
         */
        fun open(
            configuration: RealmConfiguration,
        ): Realm {
            // TODO Find a cleaner way to get the initial frozen instance
            val liveRealm = RealmInterop.realm_open(configuration.nativeConfig)
            val frozenRealm = RealmInterop.realm_freeze(liveRealm)
            RealmInterop.realm_close(liveRealm)
            val realm = Realm(configuration, frozenRealm)
            realm.log.info("Opened Realm: ${configuration.path}")
            return realm
        }
    }

    init {
        // Update the Realm if another process or the Sync Client updates the Realm
        realmScope.launch {
            realmFlow.emit(this@Realm)
            notifier.realmChanged().collect { (dbPointer: NativePointer, version: VersionId) ->
                updateRealmPointer(dbPointer, version)
            }
        }
    }

    /**
     * Open a Realm instance. This instance grants access to an underlying Realm file defined by
     * the provided [RealmConfiguration].
     *
     * FIXME Figure out how to describe the constructor better
     * FIXME Once the implementation of this class moves to the frozen architecture
     *  this constructor should be the primary way to open Realms (as you only need
     *  to do it once pr. app).
     */
    public constructor(configuration: RealmConfiguration) :
        this(configuration, RealmInterop.realm_open(configuration.nativeConfig))

    /**
     * Modify the underlying Realm file in a suspendable transaction on the default Realm Write
     * Dispatcher.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if
     * the calling Realm not yet represent this.
     *
     * Write transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block. If this is a RealmObject it is
     * frozen before being returned.
     * @see [RealmConfiguration.writeDispatcher]
     */
    suspend fun <R> write(block: MutableRealm.() -> R): R {
        @Suppress("TooGenericExceptionCaught") // FIXME https://github.com/realm/realm-kotlin/issues/70
        try {
            val (nativePointer, versionId, result) = this.writer.write(block)
            // Update the user facing Realm before returning the result.
            // That way, querying the Realm right after the `write` completes will return
            // the written data. Otherwise, we would have to wait for the Notifier thread
            // to detect it and update the user Realm.
            updateRealmPointer(nativePointer, versionId)
            return result
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Modify the underlying Realm file while blocking the calling thread until the transaction is
     * done. Write transactions automatically commit any changes made when the closure returns
     * unless [MutableRealm.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if the calling
     * Realm not yet represent this.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block.
     *
     * @throws IllegalStateException if invoked inside an existing transaction.
     */
    fun <R> writeBlocking(block: MutableRealm.() -> R): R {
        writer.checkInTransaction("Cannot initiate transaction when already in a write transaction")
        return runBlocking {
            write(block)
        }
    }

    /**
     * Observe changes to the Realm. If there is any change to the Realm, the flow will emit the
     * updated Realm. The flow will continue running indefinitely until canceled.
     *
     * The latest version of the Realm will be immediately emitted as the first element when
     * [Flow.collect] is called.
     *
     * The change calculations will automatically run on on the thread represented by
     * [RealmConfiguration.notificationDispatcher].
     *
     * @return a flow representing changes to this Realm.
     */
    public fun observe(): Flow<Realm> {
        return realmFlow.asSharedFlow()
    }

    /**
     * Observe changes to the Realm. If there is any change to the Realm, the flow will emit the
     * updated Realm. The flow will continue running indefinitely until canceled.
     *
     * The latest version of the Realm will be immediately emitted as the first element when
     * [Flow.collect] is called.
     *
     * The change calculations will automatically run on on the thread represented by
     * [RealmConfiguration.notificationDispatcher].
     *
     * @param callback callback to be notified whenever the underlying Realm object changes.
     *
     * @return a token that can be used to cancel further notifications and free the
     * underlying resources. Failing to cancel the listener will result in a memory leak.
     */
    public fun addChangeListener(callback: Callback<Realm>): Cancellable {
        val job = realmScope.launch {
            realmFlow.collect {
                callback.onChange(it)
            }
        }
        return FlowNotificationToken(callback, job)
    }

    internal override fun <T : RealmObject> registerResultsObserver(results: RealmResults<T>): Flow<RealmResults<T>> {
        return notifier.resultsChanged(results)
    }

    internal override fun <T : RealmObject> registerListObserver(list: List<T>): Flow<List<T>> {
        return notifier.listChanged(list)
    }

    internal override fun <T : RealmObject> registerObjectObserver(obj: T): Flow<T> {
        return notifier.objectChanged(obj)
    }

    internal override fun <T : RealmObject> registerResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        val job = realmScope.launch {
            notifier.resultsChanged(results).collect {
                callback.onChange(it)
            }
        }
        return FlowNotificationToken(callback, job)
    }

    internal override fun <T : RealmObject> registerListChangeListener(list: List<T>, callback: Callback<List<T>?>): Cancellable {
        TODO("Not yet implemented")
    }

    internal override fun <T : RealmObject> registerObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        val job = realmScope.launch {
            notifier.objectChanged(obj)
                .onCompletion { error: Throwable? ->
                    if (error == null) {
                        callback.onChange(null)
                    } else {
                        // TODO How should we handle errors?
                        throw error
                    }
                }
                .collect {
                    callback.onChange(it)
                }
        }
        return FlowNotificationToken(callback, job)
    }

    private suspend fun updateRealmPointer(newPointer: NativePointer, newVersion: VersionId) {
        realmPointerMutex.withLock {
            log.debug("Updating Realm version: $version -> $newVersion")
            val newRealmReference = RealmReference(this, newPointer)
            // If we advance to a newer version then we should keep track of the preceeding one,
            // otherwise just track the new one directly.
            val untrackedReference = if (newVersion >= version) {
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

    /**
     * Close this Realm and all underlying resources. Accessing any methods or Realm Objects after this
     * method has been called will then an [IllegalStateException].
     *
     * This will block until underlying Realms (writer and notifier) are closed, including rolling
     * back any ongoing transactions when [close] is called. Calling this from the Realm Write
     * Dispatcher while inside a transaction block will throw, while calling this by some means of
     * a blocking operation on another thread (e.g. `runBlocking(Dispatcher.Default)`) inside a
     * transaction cause a deadlock.
     *
     * @throws IllegalStateException if called from the Realm Write Dispatcher while inside a
     * transaction block.
     */
    public override fun close() {
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
