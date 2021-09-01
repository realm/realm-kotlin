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

package io.realm.interop

import io.realm.interop.RealmCoreException
import realm_wrapper.realm_schema_mode
import realm_wrapper.realm_schema_mode_e

// Interfaces to hold C API enum from cinterop
interface NativeEnum<T : Enum<T>> {
    val nativeValue: Enum<T>
}

// Interfaces to hold C API enumerated constant from cinterop
interface NativeEnumerated {
    val nativeValue: UInt
}

// FIXME API-SCHEMA On JVM actuals cannot be combined in same file. Consider replicating that split here too,
//  but await final placement.

actual enum class SchemaMode(override val nativeValue: realm_schema_mode) : NativeEnum<realm_schema_mode> {
    RLM_SCHEMA_MODE_AUTOMATIC(realm_schema_mode_e.RLM_SCHEMA_MODE_AUTOMATIC),
    RLM_SCHEMA_MODE_IMMUTABLE(realm_schema_mode_e.RLM_SCHEMA_MODE_IMMUTABLE),
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE(realm_schema_mode_e.RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE),
    RLM_SCHEMA_MODE_RESET_FILE(realm_schema_mode_e.RLM_SCHEMA_MODE_RESET_FILE),
    RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED(realm_schema_mode_e.RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED),
    RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT(realm_schema_mode_e.RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT),
    RLM_SCHEMA_MODE_MANUAL(realm_schema_mode_e.RLM_SCHEMA_MODE_MANUAL),
}

actual enum class ClassFlag(override val nativeValue: UInt) : NativeEnumerated {
    RLM_CLASS_NORMAL(realm_wrapper.RLM_CLASS_NORMAL),
    RLM_CLASS_EMBEDDED(realm_wrapper.RLM_CLASS_EMBEDDED),
}

actual enum class PropertyType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_PROPERTY_TYPE_INT(realm_wrapper.RLM_PROPERTY_TYPE_INT),
    RLM_PROPERTY_TYPE_BOOL(realm_wrapper.RLM_PROPERTY_TYPE_BOOL),
    RLM_PROPERTY_TYPE_STRING(realm_wrapper.RLM_PROPERTY_TYPE_STRING),
    RLM_PROPERTY_TYPE_OBJECT(realm_wrapper.RLM_PROPERTY_TYPE_OBJECT),
    RLM_PROPERTY_TYPE_FLOAT(realm_wrapper.RLM_PROPERTY_TYPE_FLOAT),
    RLM_PROPERTY_TYPE_DOUBLE(realm_wrapper.RLM_PROPERTY_TYPE_DOUBLE),
}

actual enum class CollectionType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_COLLECTION_TYPE_NONE(realm_wrapper.RLM_COLLECTION_TYPE_NONE),
    RLM_COLLECTION_TYPE_LIST(realm_wrapper.RLM_COLLECTION_TYPE_LIST),
    RLM_COLLECTION_TYPE_SET(realm_wrapper.RLM_COLLECTION_TYPE_SET),
    RLM_COLLECTION_TYPE_DICTIONARY(realm_wrapper.RLM_COLLECTION_TYPE_DICTIONARY),
}

actual enum class PropertyFlag(override val nativeValue: UInt) : NativeEnumerated {
    RLM_PROPERTY_NORMAL(realm_wrapper.RLM_PROPERTY_NORMAL),
    RLM_PROPERTY_NULLABLE(realm_wrapper.RLM_PROPERTY_NULLABLE),
    RLM_PROPERTY_PRIMARY_KEY(realm_wrapper.RLM_PROPERTY_PRIMARY_KEY),
    RLM_PROPERTY_INDEXED(realm_wrapper.RLM_PROPERTY_INDEXED),
}

actual enum class SchemaValidationMode(override val nativeValue: UInt) : NativeEnumerated {
    RLM_SCHEMA_VALIDATION_BASIC(realm_wrapper.RLM_SCHEMA_VALIDATION_BASIC),
    RLM_SCHEMA_VALIDATION_SYNC(realm_wrapper.RLM_SCHEMA_VALIDATION_SYNC),
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS(realm_wrapper.RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS),
}

actual enum class ErrorType(override val nativeValue: UInt) : NativeEnumerated {
    RLM_ERR_NONE(realm_wrapper.realm_errno.RLM_ERR_NONE.value),
    RLM_ERR_UNKNOWN(realm_wrapper.realm_errno.RLM_ERR_UNKNOWN.value),
    RLM_ERR_OTHER_EXCEPTION(realm_wrapper.realm_errno.RLM_ERR_OTHER_EXCEPTION.value),
    RLM_ERR_OUT_OF_MEMORY(realm_wrapper.realm_errno.RLM_ERR_OUT_OF_MEMORY.value),
    RLM_ERR_NOT_CLONABLE(realm_wrapper.realm_errno.RLM_ERR_NOT_CLONABLE.value),
    RLM_ERR_NOT_IN_A_TRANSACTION(realm_wrapper.realm_errno.RLM_ERR_NOT_IN_A_TRANSACTION.value),
    RLM_ERR_WRONG_THREAD(realm_wrapper.realm_errno.RLM_ERR_WRONG_THREAD.value),
    RLM_ERR_INVALIDATED_OBJECT(realm_wrapper.realm_errno.RLM_ERR_INVALIDATED_OBJECT.value),
    RLM_ERR_INVALID_PROPERTY(realm_wrapper.realm_errno.RLM_ERR_INVALID_PROPERTY.value),
    RLM_ERR_MISSING_PROPERTY_VALUE(realm_wrapper.realm_errno.RLM_ERR_MISSING_PROPERTY_VALUE.value),
    RLM_ERR_PROPERTY_TYPE_MISMATCH(realm_wrapper.realm_errno.RLM_ERR_PROPERTY_TYPE_MISMATCH.value),
    RLM_ERR_MISSING_PRIMARY_KEY(realm_wrapper.realm_errno.RLM_ERR_MISSING_PRIMARY_KEY.value),
    RLM_ERR_UNEXPECTED_PRIMARY_KEY(realm_wrapper.realm_errno.RLM_ERR_UNEXPECTED_PRIMARY_KEY.value),
    RLM_ERR_WRONG_PRIMARY_KEY_TYPE(realm_wrapper.realm_errno.RLM_ERR_WRONG_PRIMARY_KEY_TYPE.value),
    RLM_ERR_MODIFY_PRIMARY_KEY(realm_wrapper.realm_errno.RLM_ERR_MODIFY_PRIMARY_KEY.value),
    RLM_ERR_READ_ONLY_PROPERTY(realm_wrapper.realm_errno.RLM_ERR_READ_ONLY_PROPERTY.value),
    RLM_ERR_PROPERTY_NOT_NULLABLE(realm_wrapper.realm_errno.RLM_ERR_PROPERTY_NOT_NULLABLE.value),
    RLM_ERR_INVALID_ARGUMENT(realm_wrapper.realm_errno.RLM_ERR_INVALID_ARGUMENT.value),
    RLM_ERR_LOGIC(realm_wrapper.realm_errno.RLM_ERR_LOGIC.value),
    RLM_ERR_NO_SUCH_TABLE(realm_wrapper.realm_errno.RLM_ERR_NO_SUCH_TABLE.value),
    RLM_ERR_NO_SUCH_OBJECT(realm_wrapper.realm_errno.RLM_ERR_NO_SUCH_OBJECT.value),
    RLM_ERR_CROSS_TABLE_LINK_TARGET(realm_wrapper.realm_errno.RLM_ERR_CROSS_TABLE_LINK_TARGET.value),
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION(realm_wrapper.realm_errno.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION.value),
    RLM_ERR_MULTIPLE_SYNC_AGENTS(realm_wrapper.realm_errno.RLM_ERR_MULTIPLE_SYNC_AGENTS.value),
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED(realm_wrapper.realm_errno.RLM_ERR_ADDRESS_SPACE_EXHAUSTED.value),
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED(realm_wrapper.realm_errno.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED.value),
    RLM_ERR_OUT_OF_DISK_SPACE(realm_wrapper.realm_errno.RLM_ERR_OUT_OF_DISK_SPACE.value),
    RLM_ERR_KEY_NOT_FOUND(realm_wrapper.realm_errno.RLM_ERR_KEY_NOT_FOUND.value),
    RLM_ERR_COLUMN_NOT_FOUND(realm_wrapper.realm_errno.RLM_ERR_COLUMN_NOT_FOUND.value),
    RLM_ERR_COLUMN_ALREADY_EXISTS(realm_wrapper.realm_errno.RLM_ERR_COLUMN_ALREADY_EXISTS.value),
    RLM_ERR_KEY_ALREADY_USED(realm_wrapper.realm_errno.RLM_ERR_KEY_ALREADY_USED.value),
    RLM_ERR_SERIALIZATION_ERROR(realm_wrapper.realm_errno.RLM_ERR_SERIALIZATION_ERROR.value),
    RLM_ERR_INVALID_PATH_ERROR(realm_wrapper.realm_errno.RLM_ERR_INVALID_PATH_ERROR.value),
    RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE(realm_wrapper.realm_errno.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE.value),
    RLM_ERR_INDEX_OUT_OF_BOUNDS(realm_wrapper.realm_errno.RLM_ERR_INDEX_OUT_OF_BOUNDS.value),
    RLM_ERR_INVALID_QUERY_STRING(realm_wrapper.realm_errno.RLM_ERR_INVALID_QUERY_STRING.value),
    RLM_ERR_INVALID_QUERY(realm_wrapper.realm_errno.RLM_ERR_INVALID_QUERY.value),
    RLM_ERR_CALLBACK(realm_wrapper.realm_errno.RLM_ERR_CALLBACK.value);
}
