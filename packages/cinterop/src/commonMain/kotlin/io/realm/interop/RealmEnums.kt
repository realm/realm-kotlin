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

import io.realm.interop.errors.RealmCoreError
import io.realm.interop.errors.RealmCoreException

// FIXME API-SCHEMA Should probably be somewhere else...maybe in runtime-api?
expect enum class SchemaMode {
    RLM_SCHEMA_MODE_AUTOMATIC,
    RLM_SCHEMA_MODE_IMMUTABLE,
    RLM_SCHEMA_MODE_READ_ONLY_ALTERNATIVE,
    RLM_SCHEMA_MODE_RESET_FILE,
    RLM_SCHEMA_MODE_ADDITIVE_DISCOVERED,
    RLM_SCHEMA_MODE_ADDITIVE_EXPLICIT,
    RLM_SCHEMA_MODE_MANUAL,
}

expect enum class ClassFlag {
    RLM_CLASS_NORMAL,
    RLM_CLASS_EMBEDDED,
}

expect enum class PropertyType {
    RLM_PROPERTY_TYPE_INT,
    RLM_PROPERTY_TYPE_BOOL,
    RLM_PROPERTY_TYPE_STRING,
    RLM_PROPERTY_TYPE_OBJECT,
    RLM_PROPERTY_TYPE_FLOAT,
    RLM_PROPERTY_TYPE_DOUBLE,
    ;

    // Consider adding property methods to make it easier to do generic code on all types. Or is this exactly what collection type is about
    // fun isList()
    // fun isReference()
}

expect enum class CollectionType {
    RLM_COLLECTION_TYPE_NONE,
    RLM_COLLECTION_TYPE_LIST,
    RLM_COLLECTION_TYPE_SET,
    RLM_COLLECTION_TYPE_DICTIONARY,
}

expect enum class PropertyFlag {
    RLM_PROPERTY_NORMAL,
    RLM_PROPERTY_NULLABLE,
    RLM_PROPERTY_PRIMARY_KEY,
    RLM_PROPERTY_INDEXED,
}

expect enum class SchemaValidationMode {
    RLM_SCHEMA_VALIDATION_BASIC,
    RLM_SCHEMA_VALIDATION_SYNC,
    RLM_SCHEMA_VALIDATION_REJECT_EMBEDDED_ORPHANS,
}

expect enum class ErrorType {
    RLM_ERR_NONE,
    RLM_ERR_UNKNOWN,
    RLM_ERR_OTHER_EXCEPTION,
    RLM_ERR_OUT_OF_MEMORY,
    RLM_ERR_NOT_CLONABLE,
    RLM_ERR_NOT_IN_A_TRANSACTION,
    RLM_ERR_WRONG_THREAD,
    RLM_ERR_INVALIDATED_OBJECT,
    RLM_ERR_INVALID_PROPERTY,
    RLM_ERR_MISSING_PROPERTY_VALUE,
    RLM_ERR_PROPERTY_TYPE_MISMATCH,
    RLM_ERR_MISSING_PRIMARY_KEY,
    RLM_ERR_UNEXPECTED_PRIMARY_KEY,
    RLM_ERR_WRONG_PRIMARY_KEY_TYPE,
    RLM_ERR_MODIFY_PRIMARY_KEY,
    RLM_ERR_READ_ONLY_PROPERTY,
    RLM_ERR_PROPERTY_NOT_NULLABLE,
    RLM_ERR_INVALID_ARGUMENT,
    RLM_ERR_LOGIC,
    RLM_ERR_NO_SUCH_TABLE,
    RLM_ERR_NO_SUCH_OBJECT,
    RLM_ERR_CROSS_TABLE_LINK_TARGET,
    RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION,
    RLM_ERR_MULTIPLE_SYNC_AGENTS,
    RLM_ERR_ADDRESS_SPACE_EXHAUSTED,
    RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED,
    RLM_ERR_OUT_OF_DISK_SPACE,
    RLM_ERR_KEY_NOT_FOUND,
    RLM_ERR_COLUMN_NOT_FOUND,
    RLM_ERR_COLUMN_ALREADY_EXISTS,
    RLM_ERR_KEY_ALREADY_USED,
    RLM_ERR_SERIALIZATION_ERROR,
    RLM_ERR_INVALID_PATH_ERROR,
    RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE,
    RLM_ERR_INDEX_OUT_OF_BOUNDS,
    RLM_ERR_INVALID_QUERY_STRING,
    RLM_ERR_INVALID_QUERY,
    RLM_ERR_CALLBACK;
}

fun errorTypeToThrowable(errorType: ErrorType, message: String?): Throwable {
    return when (errorType) {
        ErrorType.RLM_ERR_OUT_OF_MEMORY,
        ErrorType.RLM_ERR_MULTIPLE_SYNC_AGENTS,
        ErrorType.RLM_ERR_ADDRESS_SPACE_EXHAUSTED,
        ErrorType.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED,
        ErrorType.RLM_ERR_UNKNOWN,
        ErrorType.RLM_ERR_OUT_OF_DISK_SPACE -> RealmCoreError(errorType, message)
        // Recoverable errors
        ErrorType.RLM_ERR_OTHER_EXCEPTION -> RealmCoreException(errorType, message)
        ErrorType.RLM_ERR_NOT_CLONABLE,
        ErrorType.RLM_ERR_NOT_IN_A_TRANSACTION,
        ErrorType.RLM_ERR_WRONG_THREAD,
        ErrorType.RLM_ERR_INVALIDATED_OBJECT,
        ErrorType.RLM_ERR_INVALID_PROPERTY,
        ErrorType.RLM_ERR_MISSING_PROPERTY_VALUE,
        ErrorType.RLM_ERR_PROPERTY_TYPE_MISMATCH,
        ErrorType.RLM_ERR_MISSING_PRIMARY_KEY,
        ErrorType.RLM_ERR_UNEXPECTED_PRIMARY_KEY,
        ErrorType.RLM_ERR_WRONG_PRIMARY_KEY_TYPE,
        ErrorType.RLM_ERR_LOGIC,
        ErrorType.RLM_ERR_MODIFY_PRIMARY_KEY,
        ErrorType.RLM_ERR_READ_ONLY_PROPERTY,
        ErrorType.RLM_ERR_PROPERTY_NOT_NULLABLE,
        ErrorType.RLM_ERR_INVALID_ARGUMENT,
        ErrorType.RLM_ERR_NO_SUCH_TABLE,
        ErrorType.RLM_ERR_NO_SUCH_OBJECT,
        ErrorType.RLM_ERR_CROSS_TABLE_LINK_TARGET,
        ErrorType.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION,
        ErrorType.RLM_ERR_KEY_NOT_FOUND,
        ErrorType.RLM_ERR_COLUMN_NOT_FOUND,
        ErrorType.RLM_ERR_COLUMN_ALREADY_EXISTS,
        ErrorType.RLM_ERR_KEY_ALREADY_USED,
        ErrorType.RLM_ERR_SERIALIZATION_ERROR,
        ErrorType.RLM_ERR_INVALID_PATH_ERROR,
        ErrorType.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE,
        ErrorType.RLM_ERR_INDEX_OUT_OF_BOUNDS,
        ErrorType.RLM_ERR_INVALID_QUERY_STRING,
        ErrorType.RLM_ERR_CALLBACK,
        ErrorType.RLM_ERR_INVALID_QUERY -> RealmCoreException(errorType, message)
        else -> RuntimeException(message)
    }
}
