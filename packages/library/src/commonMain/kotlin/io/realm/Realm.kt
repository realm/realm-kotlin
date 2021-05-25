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

import io.realm.internal.SuspendableNotifier
import io.realm.internal.SuspendableWriter
import io.realm.internal.util.runBlocking
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm private constructor(configuration: RealmConfiguration, dbPointer: NativePointer) :
    BaseRealm(configuration, dbPointer) {

    // Coroutine scope used to control the lifecycle of of Writer and Notifier jobs, so
    // we can cancel them when the Realm is closed.
    private val notifierDispatcher = configuration.notifierDispatcher(configuration.path)
    internal val realmScope: CoroutineScope = CoroutineScope(SupervisorJob() + notifierDispatcher)
    internal val notifier = SuspendableNotifier(this, notifierDispatcher)
    private val writer = SuspendableWriter(this, configuration.writeDispatcher(configuration.path))
    private val realmPointerMutex = Mutex()
    // Intermediate state from close() is called to it completes(). Use to signal Notifier/Writer dispatchers that
    // they should stop as soon as possible.
    internal var isShuttingDown = false

    // FIXME: Replay should match other notifications. I believe they mit their starting state when you register a listener
    private val realmFlow = MutableSharedFlow<Realm>(replay = 1)

    companion object {
        /**
         * Default name for Realm files unless overridden by [RealmConfiguration.Builder.name].
         */
        public const val DEFAULT_FILE_NAME = "default.realm"

        /**
         * Default tag used by log entries
         */
        public const val DEFAULT_LOG_TAG = "REALM"

        fun open(configuration: RealmConfiguration): Realm {
            // TODO API-INTERNAL
            //  IN Android use lazy property delegation init to load the shared library use the
            //  function call (lazy init to do any preprocessing before starting Realm eg: log level etc)
            //  or implement an init method which is a No-OP in iOS but in Android it load the shared library
            val liveDbPointer = RealmInterop.realm_open(configuration.nativeConfig)
            val frozenDbPointer = RealmInterop.realm_freeze(liveDbPointer)
            RealmInterop.realm_close(liveDbPointer)
            val realm = Realm(configuration, frozenDbPointer)
            realm.log.info("Opened Realm: ${configuration.path}")
            return realm
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

    init {
        // Update the Realm if another process or the Sync Client updates the Realm
        val job: Job = realmScope.launch {
            notifier.realmChanged().collect {
                updateRealmPointer(it.first, it.second)
            }
        }
    }

    /**
     * Modify the underlying Realm file in a suspendable transaction on the default Realm
     * dispatcher.
     *
     * NOTE: Objects and results retrieved before a write are no longer valid. This restriction
     * will be lifted when the frozen architecture is fully in place.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if
     * the calling Realm not yet represent this.
     *
     * Write transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * @param block function that should be run within the context of a write transaction.
     * FIXME Isn't this impossible to achieve in a way where we can guarantee that we freeze all
     *  objects leaving the transaction? It currently works for RealmObjects, and can maybe be done
     *  for collections and RealmResults, but what it is bundled in other containers, etc. Should
     *  we define an interface of _returnable_ objects that follows some convention?
     * @return any value returned from the provided write block as frozen/immutable objects.
     * @see [RealmConfiguration.writeDispatcher]
     */
    // TODO Would we be able to offer a per write error handler by adding a CoroutineExceptinoHandler
    public suspend fun <R> write(block: MutableRealm.() -> R): R {
        @Suppress("TooGenericExceptionCaught") // FIXME https://github.com/realm/realm-kotlin/issues/70
        try {
            // FIXME What if the result is of a different version than the realm (some other
            //  write transaction finished before)
            return this.writer.write(block)
        } catch (e: Exception) {
            throw e
        }
    }

    internal suspend fun updateRealmPointer(newRealm: NativePointer, newVersion: VersionId) {
        realmPointerMutex.withLock {
            log.debug("$version -> $newVersion")
            if (newVersion >= version) {
                dbPointer = newRealm
                version = newVersion
            }
        }

        // Notify public observers that the Realm changed
        realmFlow.emit(this)
    }

    /**
     * Modify the underlying Realm file by creating a write transaction on the current thread. Write
     * transactions automatically commit any changes made when the closure returns unless
     * [MutableRealm.cancelWrite] was called.
     *
     * The write transaction always represent the latest version of data in the Realm file, even if the calling
     * Realm not yet represent this.
     *
     * @param block function that should be run within the context of a write transaction.
     * @return any value returned from the provided write block.
     */
    fun <R> writeBlocking(block: MutableRealm.() -> R): R {
        checkClosed()
        return runBlocking {
            write(block)
        }
    }

    /**
     * FIXME
     * Be notified whenever this Realm is updated.
     */
    public fun observe(): Flow<Realm> {
        return realmFlow.asSharedFlow()
    }

    override fun <T : RealmObject> observeResults(results: RealmResults<T>): Flow<RealmResults<T>> {
        return notifier.resultsChanged(results)
    }

    override fun <T : RealmObject> observeList(list: List<T>): Flow<List<T>> {
        return notifier.listChanged(list)
    }

    override fun <T : RealmObject> observeObject(obj: T): Flow<T> {
        return notifier.objectChanged(obj)
    }

    override fun <T : RealmObject> addResultsChangeListener(
        results: RealmResults<T>,
        callback: Callback<RealmResults<T>>
    ): Cancellable {
        return notifier.addResultsChangedListener(results, callback)
    }

    override fun <T : RealmObject> addListChangeListener(list: List<T>, callback: Callback<List<T>>): Cancellable {
        return notifier.addRealmChangedListener()
        TODO("Not yet implemented")
    }

    override fun <T : RealmObject> addObjectChangeListener(obj: T, callback: Callback<T?>): Cancellable {
        TODO("Not yet implemented")
    }

    /**
     * Close this Realm and all underlying resources. Accessing any methods or Realm Objects after this
     * method has been called will then an [IllegalStateException].
     */
    public override fun close() {
        isShuttingDown = true
        super.close()
        // TODO There is currently nothing that tears down the dispatcher. Should there be?
        realmScope.cancel("Closing Realm while it is being used: ${configuration.path}", IllegalStateException("A Realm should not be closed during a write or while listening to changes on it."))
        // FIXME: How to check if no tasks are running?
        writer.close()
        notifier.close()
        // FIXME: Closing the Realm should terminate all notifiers currently launched
    }
}
