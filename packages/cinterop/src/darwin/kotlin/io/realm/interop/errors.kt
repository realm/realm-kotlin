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

import realm_wrapper.realm_errno.*

fun coreErrorAsThrowable(nativeValue: UInt, message: String?): RealmCoreException {
    return when (nativeValue) {
        RLM_ERR_NONE.value -> RealmCoreNoneException(message)
        RLM_ERR_UNKNOWN.value -> RealmCoreUnknownException(message)
        RLM_ERR_OTHER_EXCEPTION.value -> RealmCoreOtherException(message)
        RLM_ERR_OUT_OF_MEMORY.value -> RealmCoreOutOfMemoryException(message)
        RLM_ERR_NOT_CLONABLE.value -> RealmCoreNotClonableException(message)
        RLM_ERR_NOT_IN_A_TRANSACTION.value -> RealmCoreNotInATransactionException(message)
        RLM_ERR_WRONG_THREAD.value -> RealmCoreWrongThreadException(message)
        RLM_ERR_INVALIDATED_OBJECT.value -> RealmCoreInvalidatedObjectException(message)
        RLM_ERR_INVALID_PROPERTY.value -> RealmCoreInvalidPropertyException(message)
        RLM_ERR_MISSING_PROPERTY_VALUE.value -> RealmCoreMissingPropertyValueException(message)
        RLM_ERR_PROPERTY_TYPE_MISMATCH.value -> RealmCorePropertyTypeMismatchException(message)
        RLM_ERR_MISSING_PRIMARY_KEY.value -> RealmCoreMissingPrimaryKeyException(message)
        RLM_ERR_UNEXPECTED_PRIMARY_KEY.value -> RealmCoreUnexpectedPrimaryKeyException(message)
        RLM_ERR_WRONG_PRIMARY_KEY_TYPE.value -> RealmCoreWrongPrimaryKeyTypeException(message)
        RLM_ERR_MODIFY_PRIMARY_KEY.value -> RealmCoreModifyPrimaryKeyException(message)
        RLM_ERR_READ_ONLY_PROPERTY.value -> RealmCoreReadOnlyPropertyException(message)
        RLM_ERR_PROPERTY_NOT_NULLABLE.value -> RealmCorePropertyNotNullableException(message)
        RLM_ERR_INVALID_ARGUMENT.value -> RealmCoreInvalidArgumentException(message)
        RLM_ERR_LOGIC.value -> RealmCoreLogicException(message)
        RLM_ERR_NO_SUCH_TABLE.value -> RealmCoreNoSuchTableException(message)
        RLM_ERR_NO_SUCH_OBJECT.value -> RealmCoreNoSuchObjectException(message)
        RLM_ERR_CROSS_TABLE_LINK_TARGET.value -> RealmCoreCrossTableLinkTargetException(message)
        RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION.value -> RealmCoreUnsupportedFileFormatVersionException(message)
        RLM_ERR_MULTIPLE_SYNC_AGENTS.value -> RealmCoreMultipleSyncAgentsException(message)
        RLM_ERR_ADDRESS_SPACE_EXHAUSTED.value -> RealmCoreAddressSpaceExhaustedException(message)
        RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED.value -> RealmCoreMaximumFileSizeExceededException(message)
        RLM_ERR_OUT_OF_DISK_SPACE.value -> RealmCoreOutOfDiskSpaceException(message)
        RLM_ERR_KEY_NOT_FOUND.value -> RealmCoreKeyNotFoundException(message)
        RLM_ERR_COLUMN_NOT_FOUND.value -> RealmCoreColumnNotFoundException(message)
        RLM_ERR_COLUMN_ALREADY_EXISTS.value -> RealmCoreColumnAlreadyExistsException(message)
        RLM_ERR_KEY_ALREADY_USED.value -> RealmCoreKeyAlreadyUsedException(message)
        RLM_ERR_SERIALIZATION_ERROR.value -> RealmCoreSerializationErrorException(message)
        RLM_ERR_INVALID_PATH_ERROR.value -> RealmCoreInvalidPathErrorException(message)
        RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE.value -> RealmCoreDuplicatePrimaryKeyValueException(message)
        RLM_ERR_INDEX_OUT_OF_BOUNDS.value -> RealmCoreIndexOutOfBoundsException(message)
        RLM_ERR_INVALID_QUERY_STRING.value -> RealmCoreInvalidQueryStringException(message)
        RLM_ERR_INVALID_QUERY.value -> RealmCoreInvalidQueryException(message)
        RLM_ERR_CALLBACK.value -> RealmCoreCallbackException(message)
        else -> RealmCoreUnknownException(message)
    }
}
