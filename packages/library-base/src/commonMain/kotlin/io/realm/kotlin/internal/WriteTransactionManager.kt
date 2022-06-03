/*
 * Copyright 2022 Realm Inc.
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

import io.realm.kotlin.internal.interop.RealmInterop

/**
 * Interface holding default implementation of methods related to controlling a write transaction.
 */
internal interface WriteTransactionManager {
    val realmReference: LiveRealmReference

    fun beginTransaction() {
        try {
            RealmInterop.realm_begin_write(realmReference.dbPointer)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Cannot begin the write transaction"
            )
        }
    }

    fun isInTransaction(): Boolean {
        return RealmInterop.realm_is_in_transaction(realmReference.dbPointer)
    }

    fun commitTransaction() {
        RealmInterop.realm_commit(realmReference.dbPointer)
    }

    fun cancelWrite() {
        try {
            RealmInterop.realm_rollback(realmReference.dbPointer)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Cannot cancel the write transaction"
            )
        }
    }
}
