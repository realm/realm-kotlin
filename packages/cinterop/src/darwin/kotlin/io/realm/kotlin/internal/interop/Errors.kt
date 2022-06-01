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

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import realm_wrapper.realm_errno

actual object CoreErrorConverter {
    private val converter: AtomicRef<((RealmCoreException) -> Throwable)?> = atomic(null)
    actual fun initialize(coreErrorConverter: (RealmCoreException) -> Throwable) {
        converter.value = coreErrorConverter
    }
    actual fun convertCoreError(coreError: RealmCoreException): Throwable {
        return converter.value!!.invoke(coreError)
    }
}

@Suppress("ComplexMethod")
fun coreErrorAsThrowable(nativeValue: realm_errno, message: String?): Throwable {
    return CoreErrorConverter.convertCoreError(
        when (nativeValue) {
            realm_errno.RLM_ERR_NONE -> RealmCoreNoneException(message)
            realm_errno.RLM_ERR_UNKNOWN -> RealmCoreUnknownException(message)
            realm_errno.RLM_ERR_OTHER_EXCEPTION -> RealmCoreOtherException(message)
            realm_errno.RLM_ERR_OUT_OF_MEMORY -> RealmCoreOutOfMemoryException(message)
            realm_errno.RLM_ERR_NOT_CLONABLE -> RealmCoreNotClonableException(message)
            realm_errno.RLM_ERR_NOT_IN_A_TRANSACTION -> RealmCoreNotInATransactionException(message)
            realm_errno.RLM_ERR_WRONG_THREAD -> RealmCoreWrongThreadException(message)
            realm_errno.RLM_ERR_INVALIDATED_OBJECT -> RealmCoreInvalidatedObjectException(message)
            realm_errno.RLM_ERR_INVALID_PROPERTY -> RealmCoreInvalidPropertyException(message)
            realm_errno.RLM_ERR_MISSING_PROPERTY_VALUE -> RealmCoreMissingPropertyValueException(message)
            realm_errno.RLM_ERR_PROPERTY_TYPE_MISMATCH -> RealmCorePropertyTypeMismatchException(message)
            realm_errno.RLM_ERR_MISSING_PRIMARY_KEY -> RealmCoreMissingPrimaryKeyException(message)
            realm_errno.RLM_ERR_UNEXPECTED_PRIMARY_KEY -> RealmCoreUnexpectedPrimaryKeyException(message)
            realm_errno.RLM_ERR_WRONG_PRIMARY_KEY_TYPE -> RealmCoreWrongPrimaryKeyTypeException(message)
            realm_errno.RLM_ERR_MODIFY_PRIMARY_KEY -> RealmCoreModifyPrimaryKeyException(message)
            realm_errno.RLM_ERR_READ_ONLY_PROPERTY -> RealmCoreReadOnlyPropertyException(message)
            realm_errno.RLM_ERR_PROPERTY_NOT_NULLABLE -> RealmCorePropertyNotNullableException(message)
            realm_errno.RLM_ERR_INVALID_ARGUMENT -> RealmCoreInvalidArgumentException(message)
            realm_errno.RLM_ERR_LOGIC -> RealmCoreLogicException(message)
            realm_errno.RLM_ERR_NO_SUCH_TABLE -> RealmCoreNoSuchTableException(message)
            realm_errno.RLM_ERR_NO_SUCH_OBJECT -> RealmCoreNoSuchObjectException(message)
            realm_errno.RLM_ERR_CROSS_TABLE_LINK_TARGET -> RealmCoreCrossTableLinkTargetException(message)
            realm_errno.RLM_ERR_UNSUPPORTED_FILE_FORMAT_VERSION -> RealmCoreUnsupportedFileFormatVersionException(message)
            realm_errno.RLM_ERR_MULTIPLE_SYNC_AGENTS -> RealmCoreMultipleSyncAgentsException(message)
            realm_errno.RLM_ERR_ADDRESS_SPACE_EXHAUSTED -> RealmCoreAddressSpaceExhaustedException(message)
            realm_errno.RLM_ERR_MAXIMUM_FILE_SIZE_EXCEEDED -> RealmCoreMaximumFileSizeExceededException(message)
            realm_errno.RLM_ERR_OUT_OF_DISK_SPACE -> RealmCoreOutOfDiskSpaceException(message)
            realm_errno.RLM_ERR_KEY_NOT_FOUND -> RealmCoreKeyNotFoundException(message)
            realm_errno.RLM_ERR_COLUMN_NOT_FOUND -> RealmCoreColumnNotFoundException(message)
            realm_errno.RLM_ERR_COLUMN_ALREADY_EXISTS -> RealmCoreColumnAlreadyExistsException(message)
            realm_errno.RLM_ERR_KEY_ALREADY_USED -> RealmCoreKeyAlreadyUsedException(message)
            realm_errno.RLM_ERR_SERIALIZATION_ERROR -> RealmCoreSerializationErrorException(message)
            realm_errno.RLM_ERR_INVALID_PATH_ERROR -> RealmCoreInvalidPathErrorException(message)
            realm_errno.RLM_ERR_DUPLICATE_PRIMARY_KEY_VALUE -> RealmCoreDuplicatePrimaryKeyValueException(message)
            realm_errno.RLM_ERR_INDEX_OUT_OF_BOUNDS -> RealmCoreIndexOutOfBoundsException(message)
            realm_errno.RLM_ERR_INVALID_QUERY_STRING -> RealmCoreInvalidQueryStringException(message)
            realm_errno.RLM_ERR_INVALID_QUERY -> RealmCoreInvalidQueryException(message)
            realm_errno.RLM_ERR_CALLBACK -> RealmCoreCallbackException(message)
            realm_errno.RLM_ERR_FILE_ACCESS_ERROR -> RealmCoreFileAccessErrorException(message)
            realm_errno.RLM_ERR_FILE_PERMISSION_DENIED -> RealmCoreFilePermissionDeniedException(message)
            realm_errno.RLM_ERR_DELETE_OPENED_REALM -> RealmCoreDeleteOpenRealmException(message)
            realm_errno.RLM_ERR_ILLEGAL_OPERATION -> RealmCoreIllegalOperationException(message)
            else -> RealmCoreUnknownException(message)
        }
    )
}
