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

import io.realm.internal.RealmReference
import io.realm.internal.SuspendableWriter
import io.realm.internal.WeakReference
import io.realm.internal.runBlocking
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// TODO API-PUBLIC Document platform specific internals (RealmInitilizer, etc.)
class Realm private constructor(configuration: RealmConfiguration, dbPointer: NativePointer) :
    BaseRealm(configuration, dbPointer) {

    private val writer: SuspendableWriter = SuspendableWriter(this)
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
            updatableRealm!!.value = value
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

        fun open(realmConfiguration: RealmConfiguration): Realm {
            // TODO Find a cleaner way to get the initial frozen instance
            val liveRealm = RealmInterop.realm_open(realmConfiguration.nativeConfig)
            val frozenRealm = RealmInterop.realm_freeze(liveRealm)
            RealmInterop.realm_close(liveRealm)
            val realm = Realm(realmConfiguration, frozenRealm)
            realm.log.info("Opened Realm: ${realmConfiguration.path}")
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
