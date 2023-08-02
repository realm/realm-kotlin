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

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmSchedulerPointer
import io.realm.kotlin.internal.platform.runBlocking
import io.realm.kotlin.internal.platform.threadId
import io.realm.kotlin.internal.schema.RealmClassImpl
import io.realm.kotlin.internal.schema.RealmSchemaImpl
import io.realm.kotlin.query.RealmQuery
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

/**
 * A _suspendable writer_ to handle all asynchronous updates to a Realm through a suspendable API.
 *
 * NOTE:
 * - The _writer_ is initialized with a dispatcher that MUST only be backed by a single thread.
 * - All operations accessing the writer's realm MUST be done in the context of the dispatcher or
 *   it's thread.
 *
 * @param owner The Realm instance needed for emitting updates.
 * @param dispatcherHolder The dispatcher on which to execute all the writers operations on.
 */
internal class SuspendableWriter(
    private val owner: RealmImpl,
    val dispatcher: CoroutineDispatcher,
    private val scheduler: RealmSchedulerPointer,
) :
    LiveRealmHolder<SuspendableWriter.WriterRealm>() {
    private val tid: ULong

    internal inner class WriterRealm :
        LiveRealm(
            owner = owner,
            configuration = owner.configuration,
            dispatcher = dispatcher,
            scheduler = scheduler
        ),
        InternalMutableRealm,
        InternalTypedRealm,
        WriteTransactionManager {

        override val realmReference: LiveRealmReference
            get() = super.realmReference

        override fun <T : TypedRealmObject> query(
            clazz: KClass<T>,
            query: String,
            vararg args: Any?
        ): RealmQuery<T> {
            return super.query(clazz, query, *args)
        }

        override fun cancelWrite() { super.cancelWrite() }
    }

    override val realmInitializer: Lazy<WriterRealm> = lazy {
        WriterRealm()
    }

    // Must only be accessed from the dispatchers thread
    override val realm: WriterRealm by realmInitializer
    private val shouldClose = kotlinx.atomicfu.atomic<Boolean>(false)
    private val transactionMutex = Mutex(false)

    init {
        tid = runBlocking(dispatcher) { threadId() }
    }

    // Currently just for internal-only usage in test, thus API is not polished
    suspend fun updateSchema(schema: RealmSchemaImpl) {
        return withContext(dispatcher) {
            transactionMutex.withLock {
                realm.log.debug("Updating schema: $schema")
                val classPropertyList = schema.classes.map { realmClass: RealmClassImpl ->
                    realmClass.cinteropClass to realmClass.cinteropProperties
                }
                val newCinteropSchema = RealmInterop.realm_schema_new(classPropertyList)
                RealmInterop.realm_update_schema(realm.realmReference.dbPointer, newCinteropSchema)
                // Are we guaranteed that updating the schema will trigger both:
                // - onSchemaChanged - invalidating the key caches
                // - onRealmChanged - updating the realm.snapshot to also point to the latest key cache
                // Seems like order is not guaranteed, but it is synchroneous, so updating snapshot
                // in both callbacks should ensure that we have the right snapshot here
                realm.updateSnapshot()
            }
        }
    }

    suspend fun <R> write(block: MutableRealm.() -> R): R {
        // TODO Would we be able to offer a per write error handler by adding a CoroutineExceptionHandler
        return withContext(dispatcher) {
            var result: R

            transactionMutex.withLock {
                try {
                    realm.beginTransaction()
                    ensureActive()
                    result = block(realm)
                    ensureActive()
                    if (!shouldClose.value && realm.isInTransaction()) {
                        realm.commitTransaction()
                    } else {
                        if (shouldClose.value)
                            throw IllegalStateException("Cannot commit transaction on closed realm")
                    }
                } catch (e: IllegalStateException) {
                    if (realm.isInTransaction()) {
                        realm.cancelWrite()
                    }
                    throw e
                }
            }
            realm.updateSnapshot()
            if (shouldFreezeWriteReturnValue(result)) {
                // Freeze the result in the context of the Dispatcher. The dispatcher should be
                // single-threaded so will guarantee that no other threads can modify the Realm
                // between the transaction is committed and we freeze it.
                // TODO Can we guarantee the Dispatcher is single-threaded? Or otherwise
                //  lock this code?
                val newReference = realm.gcTrackedSnapshot()
                freezeWriteReturnValue(newReference, result)
            } else {
                result
            }
        }
    }

    private fun <R> freezeWriteReturnValue(reference: RealmReference, result: R): R {
        @Suppress("UNCHECKED_CAST")
        return when (result) {
            // is RealmResults<*> -> result.freeze(this) as R
            is BaseRealmObject -> {
                // FIXME If we could transfer ownership (the owning Realm) in Realm instead then we
                //  could completely eliminate the need for the external owner in here!?
                result.runIfManaged {
                    // Invalid objects are returned as-is. We assume the caller know what they
                    // are doing and will either throw the result away or treat it accordingly.
                    // See https://github.com/realm/realm-kotlin/issues/1300 for context.
                    when (result.isValid()) {
                        true -> freeze(reference)!!.toRealmObject()
                        false -> result
                    }
                }
            }
            else -> throw IllegalArgumentException("Did not recognize type to be frozen: $result")
        } as R
    }

    private fun <R> shouldFreezeWriteReturnValue(result: R): Boolean {
        // How to test for managed results?
        return when (result) {
            // is RealmResults<*> -> return result.owner != null
            is BaseRealmObject -> return result.isManaged()
            else -> false
        }
    }

    // Checks if the current thread is already executing a transaction
    internal fun checkInTransaction(message: String) {
        if (tid == threadId() && transactionMutex.isLocked) {
            throw IllegalStateException(message)
        }
    }

    fun close() {
        // runBlocking cannot be called on the dispatcher thread as this will deadlock if called
        // inside a transaction. This is already guarded in Realm.close calling this, but keep it
        // for safety while evaluating if we want to allow closing the realm from inside a
        // transaction (which should then just be implemented without runBlocking when we are
        // already on the correct thread).
        checkInTransaction("Cannot close in a transaction block")
        runBlocking {
            // TODO OPTIMIZE We are currently awaiting any running transaction to finish before
            //  actually closing the realm, as we cannot schedule something to run on the dispatcher
            //  and closing the realm from another thread during a transaction causes race
            //  conditions/crashed. Maybe signal this faster by canceling the users scope of the
            //  transaction, etc.
            shouldClose.value = true
            // We have verified that we are not on the dispatcher thread, so safe to schedule this
            // which will itself prevent other transactions to start as the dispatcher can only run
            // a single job at a time
            withContext(dispatcher) {
                // Calling close on a non initialized Realm is wasteful since before calling RealmInterop.close
                // The Realm will be first opened (RealmInterop.open) and an instance created in vain.
                if (realmInitializer.isInitialized()) {
                    realm.close()
                }
            }
        }
    }
}
