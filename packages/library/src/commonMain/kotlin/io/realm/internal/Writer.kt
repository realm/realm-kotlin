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
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.VersionId
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class Writer(configuration: RealmConfiguration, val dispatcher: CoroutineDispatcher) {
    private val realm: MutableRealm by lazy {
            MutableRealm(configuration)
    }

    suspend fun <R> write(block: MutableRealm.() -> R): Triple<NativePointer, VersionId, R> {
        // TODO Would we be able to offer a per write error handler by adding a CoroutineExceptionHandler
        return withContext(dispatcher) {
            // TODO Should we ensure that we are still active
            var result: R
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
            // TODO: Can we guarantee the Dispatcher is single-threaded? Or otherwise
            //  lock this code?
            val newDbPointer = RealmInterop.realm_freeze(realm.dbPointer)
            val newVersion = VersionId(RealmInterop.realm_get_version_id(newDbPointer))
            if (shouldFreezeWriteReturnValue(result)) {
                result = freezeWriteReturnValue(result, newDbPointer)
            }
            Triple(newDbPointer, newVersion, result)
        }
    }

    private fun <R> freezeWriteReturnValue(result: R, frozenDbPointer: NativePointer): R {
        return when(result) {
//            is RealmResults<*> -> result.freeze(this) as R
            is RealmObject -> {
                val obj: RealmObjectInternal = (result as RealmObjectInternal)
                obj.freeze<RealmObject>(realm.dbPointer, frozenDbPointer) as R
            }
            else -> throw IllegalArgumentException("Did not recognize type to be frozen: $result")
        }
    }

    private fun <R> shouldFreezeWriteReturnValue(result: R): Boolean {
        // How to test for managed results?
        return when(result) {
//            is RealmResults<*> -> return result.owner != null
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

