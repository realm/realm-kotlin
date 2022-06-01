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

package io.realm.kotlin.internal.interop

/**
 * Generic representation of a Realm-Core exception.
 */
sealed class RealmCoreException(message: String?) : Exception(message)

// RLM_ERR_NONE
class RealmCoreNoneException(message: String?) : RealmCoreException(message)
// RLM_ERR_UNKNOWN
class RealmCoreUnknownException(message: String?) : RealmCoreException(message)
// RLM_ERR_OTHER_EXCEPTION
class RealmCoreOtherException(message: String?) : RealmCoreException(message)
// RLM_ERR_OUT_OF_MEMORY
class RealmCoreOutOfMemoryException(message: String?) : RealmCoreException(message)
// RLM_ERR_NOT_CLONABLE
class RealmCoreNotClonableException(message: String?) : RealmCoreException(message)
// RLM_ERR_NOT_IN_A_TRANSACTION
class RealmCoreNotInATransactionException(message: String?) : RealmCoreException(message)
// RLM_ERR_WRONG_THREAD
class RealmCoreWrongThreadException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALIDATED_OBJECT
class RealmCoreInvalidatedObjectException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALID_PROPERTY
class RealmCoreInvalidPropertyException(message: String?) : RealmCoreException(message)
// RLM_ERR_MISSING_PROPERTY_VALUE
class RealmCoreMissingPropertyValueException(message: String?) : RealmCoreException(message)
// RLM_ERR_PROPERTY_TYPE_MISMATCH
class RealmCorePropertyTypeMismatchException(message: String?) : RealmCoreException(message)
// RLM_ERR_MISSING_PRIMARY_KEY
class RealmCoreMissingPrimaryKeyException(message: String?) : RealmCoreException(message)
// RLM_ERR_UNEXPECTED_PRIMARY_KEY
class RealmCoreUnexpectedPrimaryKeyException(message: String?) : RealmCoreException(message)
// RLM_ERR_WRONG_PRIMARY_KEY_TYPE
class RealmCoreWrongPrimaryKeyTypeException(message: String?) : RealmCoreException(message)
// RLM_ERR_MODIFY_PRIMARY_KEY
class RealmCoreModifyPrimaryKeyException(message: String?) : RealmCoreException(message)
// RLM_ERR_READ_ONLY_PROPERTY
class RealmCoreReadOnlyPropertyException(message: String?) : RealmCoreException(message)
// RLM_ERR_PROPERTY_NOT_NULLABLE
class RealmCorePropertyNotNullableException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALID_ARGUMENT
class RealmCoreInvalidArgumentException(message: String?) : RealmCoreException(message)
// RLM_ERR_LOGIC
class RealmCoreLogicException(message: String?) : RealmCoreException(message)
// RLM_ERR_NO_SUCH_TABLE
class RealmCoreNoSuchTableException(message: String?) : RealmCoreException(message)
// RLM_ERR_NO_SUCH_OBJECT
class RealmCoreNoSuchObjectException(message: String?) : RealmCoreException(message)
// RLM_ERR_CROSS_TABLE_LINK_TARGET
class RealmCoreCrossTableLinkTargetException(message: String?) : RealmCoreException(message)
// RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION
class RealmCoreUnsupportedFileFormatVersionException(message: String?) : RealmCoreException(message)
// RLM_ERR_MULTIPLE_SYNC_AGENTS
class RealmCoreMultipleSyncAgentsException(message: String?) : RealmCoreException(message)
// RLM_ERR_ADDRESS_SPACE_EXHAUSTED
class RealmCoreAddressSpaceExhaustedException(message: String?) : RealmCoreException(message)
// RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED
class RealmCoreMaximumFileSizeExceededException(message: String?) : RealmCoreException(message)
// RLM_ERR_OUT_OF_DISK_SPACE
class RealmCoreOutOfDiskSpaceException(message: String?) : RealmCoreException(message)
// RLM_ERR_KEY_NOT_FOUND
class RealmCoreKeyNotFoundException(message: String?) : RealmCoreException(message)
// RLM_ERR_COLUMN_NOT_FOUND
class RealmCoreColumnNotFoundException(message: String?) : RealmCoreException(message)
// RLM_ERR_COLUMN_ALREADY_EXISTS
class RealmCoreColumnAlreadyExistsException(message: String?) : RealmCoreException(message)
// RLM_ERR_KEY_ALREADY_USED
class RealmCoreKeyAlreadyUsedException(message: String?) : RealmCoreException(message)
// RLM_ERR_SERIALIZATION_ERROR
class RealmCoreSerializationErrorException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALID_PATH_ERROR
class RealmCoreInvalidPathErrorException(message: String?) : RealmCoreException(message)
// RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE
class RealmCoreDuplicatePrimaryKeyValueException(message: String?) : RealmCoreException(message)
// RLM_ERR_INDEX_OUT_OF_BOUNDS
class RealmCoreIndexOutOfBoundsException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALID_QUERY_STRING
class RealmCoreInvalidQueryStringException(message: String?) : RealmCoreException(message)
// RLM_ERR_INVALID_QUERY
class RealmCoreInvalidQueryException(message: String?) : RealmCoreException(message)
// RLM_ERR_CALLBACK
class RealmCoreCallbackException(message: String?) : RealmCoreException(message)
// RLM_ERR_FILE_ACCESS_ERROR
class RealmCoreFileAccessErrorException(message: String?) : RealmCoreException(message)
// RLM_ERR_FILE_PERMISSION_DENIED
class RealmCoreFilePermissionDeniedException(message: String?) : RealmCoreException(message)
// RLM_ERR_DELETE_OPENED_REALM
class RealmCoreDeleteOpenRealmException(message: String?) : RealmCoreException(message)
// RLM_ERR_ILLEGAL_OPERATION
class RealmCoreIllegalOperationException(message: String?) : RealmCoreException(message)
