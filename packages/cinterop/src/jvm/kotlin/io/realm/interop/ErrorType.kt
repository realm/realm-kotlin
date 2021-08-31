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

package io.realm.interop

import io.realm.interop.errors.RealmCoreException

// FIXME API-INTERNAL Compiler does not pick up the actual if not in a separate file, so not
//  following RealmEnums.kt structure, but might have to move anyway, so keeping the structure
//  unaligned for now.
actual enum class ErrorType(override val nativeValue: Int) : NativeEnumerated {
    RLM_ERR_NONE(realm_errno_e.RLM_ERR_NONE),
    RLM_ERR_UNKNOWN(realm_errno_e.RLM_ERR_UNKNOWN),
    RLM_ERR_OTHER_EXCEPTION(realm_errno_e.RLM_ERR_OTHER_EXCEPTION),
    RLM_ERR_OUT_OF_MEMORY(realm_errno_e.RLM_ERR_OUT_OF_MEMORY),
    RLM_ERR_NOT_CLONABLE(realm_errno_e.RLM_ERR_NOT_CLONABLE),
    RLM_ERR_NOT_IN_A_TRANSACTION(realm_errno_e.RLM_ERR_NOT_IN_A_TRANSACTION),
    RLM_ERR_WRONG_THREAD(realm_errno_e.RLM_ERR_WRONG_THREAD),
    RLM_ERR_INVALIDATED_OBJECT(realm_errno_e.RLM_ERR_INVALIDATED_OBJECT),
    RLM_ERR_INVALID_PROPERTY(realm_errno_e.RLM_ERR_INVALID_PROPERTY),
    RLM_ERR_MISSING_PROPERTY_VALUE(realm_errno_e.RLM_ERR_MISSING_PROPERTY_VALUE),
    RLM_ERR_PROPERTY_TYPE_MISMATCH(realm_errno_e.RLM_ERR_PROPERTY_TYPE_MISMATCH),
    RLM_ERR_MISSING_PRIMARY_KEY(realm_errno_e.RLM_ERR_MISSING_PRIMARY_KEY),
    RLM_ERR_UNEXPECTED_PRIMARY_KEY(realm_errno_e.RLM_ERR_UNEXPECTED_PRIMARY_KEY),
    RLM_ERR_WRONG_PRIMARY_KEY_TYPE(realm_errno_e.RLM_ERR_WRONG_PRIMARY_KEY_TYPE),
    RLM_ERR_MODIFY_PRIMARY_KEY(realm_errno_e.RLM_ERR_MODIFY_PRIMARY_KEY),
    RLM_ERR_READ_ONLY_PROPERTY(realm_errno_e.RLM_ERR_READ_ONLY_PROPERTY),
    RLM_ERR_PROPERTY_NOT_NULLABLE(realm_errno_e.RLM_ERR_PROPERTY_NOT_NULLABLE),
    RLM_ERR_INVALID_ARGUMENT(realm_errno_e.RLM_ERR_INVALID_ARGUMENT),
    RLM_ERR_LOGIC(realm_errno_e.RLM_ERR_LOGIC),
    RLM_ERR_NO_SUCH_TABLE(realm_errno_e.RLM_ERR_NO_SUCH_TABLE),
    RLM_ERR_NO_SUCH_OBJECT(realm_errno_e.RLM_ERR_NO_SUCH_OBJECT),
    RLM_ERR_CROSS_TABLE_LINK_TARGET(realm_errno_e.RLM_ERR_CROSS_TABLE_LINK_TARGET),
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION(realm_errno_e.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION),
    RLM_ERR_MULTIPLE_SYNC_AGENTS(realm_errno_e.RLM_ERR_MULTIPLE_SYNC_AGENTS),
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED(realm_errno_e.RLM_ERR_ADDRESS_SPACE_EXHAUSTED),
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED(realm_errno_e.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED),
    RLM_ERR_OUT_OF_DISK_SPACE(realm_errno_e.RLM_ERR_OUT_OF_DISK_SPACE),
    RLM_ERR_KEY_NOT_FOUND(realm_errno_e.RLM_ERR_KEY_NOT_FOUND),
    RLM_ERR_COLUMN_NOT_FOUND(realm_errno_e.RLM_ERR_COLUMN_NOT_FOUND),
    RLM_ERR_COLUMN_ALREADY_EXISTS(realm_errno_e.RLM_ERR_COLUMN_ALREADY_EXISTS),
    RLM_ERR_KEY_ALREADY_USED(realm_errno_e.RLM_ERR_KEY_ALREADY_USED),
    RLM_ERR_SERIALIZATION_ERROR(realm_errno_e.RLM_ERR_SERIALIZATION_ERROR),
    RLM_ERR_INVALID_PATH_ERROR(realm_errno_e.RLM_ERR_INVALID_PATH_ERROR),
    RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE(realm_errno_e.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE),
    RLM_ERR_INDEX_OUT_OF_BOUNDS(realm_errno_e.RLM_ERR_INDEX_OUT_OF_BOUNDS),
    RLM_ERR_INVALID_QUERY_STRING(realm_errno_e.RLM_ERR_INVALID_QUERY_STRING),
    RLM_ERR_INVALID_QUERY(realm_errno_e.RLM_ERR_INVALID_QUERY),
    RLM_ERR_CALLBACK(realm_errno_e.RLM_ERR_CALLBACK);

    companion object {
        private val id2ErrorMap: Map<Int, ErrorType> by lazy {
            values().map {
                it.nativeValue to it
            }.toMap()
        }

        @JvmStatic
        fun asThrowable(id: Int, message: String?): Throwable =
            RealmCoreException(if (id2ErrorMap.containsKey(id)) id2ErrorMap[id] else RLM_ERR_UNKNOWN, message)
    }
}
