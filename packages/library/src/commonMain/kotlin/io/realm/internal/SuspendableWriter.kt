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

package io.realm.internal

import io.realm.MutableRealm
import io.realm.Realm
import io.realm.RealmObject
import io.realm.VersionId
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * A _suspendable writer_ to handle all asynchronous updates to a Realm through a suspendable API.
 *
 * NOTE:
 * - The _writer_ is initialized with a dispatcher that MUST only be backed by a single thread.
 * - All operations accessing the writer's realm MUST be done in the context of the dispatcher or
 *   it's thread.
 *
 * @param configuration
 * @param dispatcher The dispatcher on which to execute all the writers operations on.
 */
class SuspendableWriter(private val owner: Realm, val dispatcher: CoroutineDispatcher) {
    // Must only be accessed from the dispatchers thread
    private val realm: MutableRealm by lazy {
        MutableRealm(owner.configuration)
    }

    suspend fun <R> write(block: MutableRealm.() -> R): Triple<NativePointer, VersionId, R> {
        // TODO Would we be able to offer a per write error handler by adding a CoroutineExceptionHandler
        return withContext(dispatcher) {
            // TODO Should we ensure that we are still active
            var result: R
            @Suppress("TooGenericExceptionCaught") // FIXME https://github.com/realm/realm-kotlin/issues/70
            try {
                realm.beginTransaction()
                result = block(realm)
                realm.commitTransaction()
            } catch (e: Exception) {
                realm.cancelWrite()
                // Should we wrap in a specific exception type like RealmWriteException?
                throw e
            }

            // Freeze the triple of <Realm, Version, Result> while in the context
            // of the Dispatcher. The dispatcher should be single-threaded so will
            // guarantee that no other threads can modify the Realm between
            // the transaction is committed and we freeze it.
            // TODO Can we guarantee the Dispatcher is single-threaded? Or otherwise
            //  lock this code?
            val newDbPointer = RealmInterop.realm_freeze(realm.transactionid.dbPointer)
            val newVersion = VersionId(RealmInterop.realm_get_version_id(newDbPointer))
            if (shouldFreezeWriteReturnValue(result)) {
                result = freezeWriteReturnValue(result, newDbPointer)
            }
            Triple(newDbPointer, newVersion, result)
        }
    }

    private fun <R> freezeWriteReturnValue(result: R, frozenDbPointer: NativePointer): R {
        return when (result) {
            // is RealmResults<*> -> result.freeze(this) as R
            is RealmObject -> {
                val obj: RealmObjectInternal = (result as RealmObjectInternal)
                @Suppress("UNCHECKED_CAST")
                obj.freeze<RealmObject>(TransactionId(owner, realm.transactionid.dbPointer), TransactionId(owner, frozenDbPointer)) as R
            }
            else -> throw IllegalArgumentException("Did not recognize type to be frozen: $result")
        }
    }

    private fun <R> shouldFreezeWriteReturnValue(result: R): Boolean {
        // How to test for managed results?
        return when (result) {
            // is RealmResults<*> -> return result.owner != null
            is RealmObject -> return result is RealmObjectInternal
            else -> false
        }
    }

    suspend fun close() {
        withContext(dispatcher) {
            realm.close()
        }
    }
}
