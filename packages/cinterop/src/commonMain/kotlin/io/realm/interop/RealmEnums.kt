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

import io.realm.interop.errors.RealmCoreAddressSpaceExhaustedException
import io.realm.interop.errors.RealmCoreCallbackException
import io.realm.interop.errors.RealmCoreColumnAlreadyExistsException
import io.realm.interop.errors.RealmCoreColumnNotFoundException
import io.realm.interop.errors.RealmCoreCrossTableLinkTargetException
import io.realm.interop.errors.RealmCoreDuplicatePrimaryKeyValueException
import io.realm.interop.errors.RealmCoreIndexOutOfBoundsException
import io.realm.interop.errors.RealmCoreInvalidArgumentException
import io.realm.interop.errors.RealmCoreInvalidPathErrorException
import io.realm.interop.errors.RealmCoreInvalidPropertyException
import io.realm.interop.errors.RealmCoreInvalidQueryException
import io.realm.interop.errors.RealmCoreInvalidQueryStringException
import io.realm.interop.errors.RealmCoreInvalidatedObjectException
import io.realm.interop.errors.RealmCoreKeyAlreadyUsedException
import io.realm.interop.errors.RealmCoreKeyNotFoundException
import io.realm.interop.errors.RealmCoreLogicException
import io.realm.interop.errors.RealmCoreMaximumFileSizeExceededException
import io.realm.interop.errors.RealmCoreMissingPrimaryKeyException
import io.realm.interop.errors.RealmCoreMissingPropertyValueException
import io.realm.interop.errors.RealmCoreModifyPrimaryKeyException
import io.realm.interop.errors.RealmCoreMultipleSyncAgentsException
import io.realm.interop.errors.RealmCoreNoSuchObjectException
import io.realm.interop.errors.RealmCoreNoSuchTableException
import io.realm.interop.errors.RealmCoreNotClonableException
import io.realm.interop.errors.RealmCoreNotInATransactionException
import io.realm.interop.errors.RealmCoreOtherException
import io.realm.interop.errors.RealmCoreOutOfDiskSpaceException
import io.realm.interop.errors.RealmCoreOutOfMemoryException
import io.realm.interop.errors.RealmCorePropertyNotNullableException
import io.realm.interop.errors.RealmCorePropertyTypeMismatchException
import io.realm.interop.errors.RealmCoreReadOnlyPropertyException
import io.realm.interop.errors.RealmCoreSerializationErrorException
import io.realm.interop.errors.RealmCoreUnexpectedPrimaryKeyException
import io.realm.interop.errors.RealmCoreUnknownException
import io.realm.interop.errors.RealmCoreUnsupportedFileFormatVersionException
import io.realm.interop.errors.RealmCoreWrongPrimaryKeyTypeException
import io.realm.interop.errors.RealmCoreWrongThreadException

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

/**
 * Maps core error types to their Kotlin exception representations.
 */
@Suppress("ComplexMethod")
fun errorTypeToThrowable(errorType: ErrorType, message: String?): Throwable {
    return when (errorType) {
        ErrorType.RLM_ERR_NONE -> throw IllegalStateException("Cannot map error type $errorType.")
        ErrorType.RLM_ERR_UNKNOWN -> RealmCoreUnknownException(message)
        ErrorType.RLM_ERR_OTHER_EXCEPTION -> RealmCoreOtherException(message)
        ErrorType.RLM_ERR_OUT_OF_MEMORY -> RealmCoreOutOfMemoryException(message)
        ErrorType.RLM_ERR_NOT_CLONABLE -> RealmCoreNotClonableException(message)
        ErrorType.RLM_ERR_NOT_IN_A_TRANSACTION -> RealmCoreNotInATransactionException(message)
        ErrorType.RLM_ERR_WRONG_THREAD -> RealmCoreWrongThreadException(message)
        ErrorType.RLM_ERR_INVALIDATED_OBJECT -> RealmCoreInvalidatedObjectException(message)
        ErrorType.RLM_ERR_INVALID_PROPERTY -> RealmCoreInvalidPropertyException(message)
        ErrorType.RLM_ERR_MISSING_PROPERTY_VALUE -> RealmCoreMissingPropertyValueException(message)
        ErrorType.RLM_ERR_PROPERTY_TYPE_MISMATCH -> RealmCorePropertyTypeMismatchException(message)
        ErrorType.RLM_ERR_MISSING_PRIMARY_KEY -> RealmCoreMissingPrimaryKeyException(message)
        ErrorType.RLM_ERR_UNEXPECTED_PRIMARY_KEY -> RealmCoreUnexpectedPrimaryKeyException(message)
        ErrorType.RLM_ERR_WRONG_PRIMARY_KEY_TYPE -> RealmCoreWrongPrimaryKeyTypeException(message)
        ErrorType.RLM_ERR_MODIFY_PRIMARY_KEY -> RealmCoreModifyPrimaryKeyException(message)
        ErrorType.RLM_ERR_READ_ONLY_PROPERTY -> RealmCoreReadOnlyPropertyException(message)
        ErrorType.RLM_ERR_PROPERTY_NOT_NULLABLE -> RealmCorePropertyNotNullableException(message)
        ErrorType.RLM_ERR_INVALID_ARGUMENT -> RealmCoreInvalidArgumentException(message)
        ErrorType.RLM_ERR_LOGIC -> RealmCoreLogicException(message)
        ErrorType.RLM_ERR_NO_SUCH_TABLE -> RealmCoreNoSuchTableException(message)
        ErrorType.RLM_ERR_NO_SUCH_OBJECT -> RealmCoreNoSuchObjectException(message)
        ErrorType.RLM_ERR_CROSS_TABLE_LINK_TARGET -> RealmCoreCrossTableLinkTargetException(message)
        ErrorType.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION -> RealmCoreUnsupportedFileFormatVersionException(message)
        ErrorType.RLM_ERR_MULTIPLE_SYNC_AGENTS -> RealmCoreMultipleSyncAgentsException(message)
        ErrorType.RLM_ERR_ADDRESS_SPACE_EXHAUSTED -> RealmCoreAddressSpaceExhaustedException(message)
        ErrorType.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED -> RealmCoreMaximumFileSizeExceededException(message)
        ErrorType.RLM_ERR_OUT_OF_DISK_SPACE -> RealmCoreOutOfDiskSpaceException(message)
        ErrorType.RLM_ERR_KEY_NOT_FOUND -> RealmCoreKeyNotFoundException(message)
        ErrorType.RLM_ERR_COLUMN_NOT_FOUND -> RealmCoreColumnNotFoundException(message)
        ErrorType.RLM_ERR_COLUMN_ALREADY_EXISTS -> RealmCoreColumnAlreadyExistsException(message)
        ErrorType.RLM_ERR_KEY_ALREADY_USED -> RealmCoreKeyAlreadyUsedException(message)
        ErrorType.RLM_ERR_SERIALIZATION_ERROR -> RealmCoreSerializationErrorException(message)
        ErrorType.RLM_ERR_INVALID_PATH_ERROR -> RealmCoreInvalidPathErrorException(message)
        ErrorType.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE -> RealmCoreDuplicatePrimaryKeyValueException(message)
        ErrorType.RLM_ERR_INDEX_OUT_OF_BOUNDS -> RealmCoreIndexOutOfBoundsException(message)
        ErrorType.RLM_ERR_INVALID_QUERY_STRING -> RealmCoreInvalidQueryStringException(message)
        ErrorType.RLM_ERR_INVALID_QUERY -> RealmCoreInvalidQueryException(message)
        ErrorType.RLM_ERR_CALLBACK -> RealmCoreCallbackException(message)
        else -> throw IllegalStateException("Error type $errorType not implemented.")
    }
}
