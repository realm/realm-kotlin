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

import io.realm.kotlin.exceptions.RealmException
import io.realm.kotlin.internal.interop.CoreErrorConverter
import io.realm.kotlin.internal.interop.RealmCoreAddressSpaceExhaustedException
import io.realm.kotlin.internal.interop.RealmCoreCallbackException
import io.realm.kotlin.internal.interop.RealmCoreColumnAlreadyExistsException
import io.realm.kotlin.internal.interop.RealmCoreColumnNotFoundException
import io.realm.kotlin.internal.interop.RealmCoreCrossTableLinkTargetException
import io.realm.kotlin.internal.interop.RealmCoreDeleteOpenRealmException
import io.realm.kotlin.internal.interop.RealmCoreDuplicatePrimaryKeyValueException
import io.realm.kotlin.internal.interop.RealmCoreException
import io.realm.kotlin.internal.interop.RealmCoreFileAccessErrorException
import io.realm.kotlin.internal.interop.RealmCoreFilePermissionDeniedException
import io.realm.kotlin.internal.interop.RealmCoreIllegalOperationException
import io.realm.kotlin.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.kotlin.internal.interop.RealmCoreInvalidArgumentException
import io.realm.kotlin.internal.interop.RealmCoreInvalidPathErrorException
import io.realm.kotlin.internal.interop.RealmCoreInvalidPropertyException
import io.realm.kotlin.internal.interop.RealmCoreInvalidQueryException
import io.realm.kotlin.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.kotlin.internal.interop.RealmCoreInvalidatedObjectException
import io.realm.kotlin.internal.interop.RealmCoreKeyAlreadyUsedException
import io.realm.kotlin.internal.interop.RealmCoreKeyNotFoundException
import io.realm.kotlin.internal.interop.RealmCoreLogicException
import io.realm.kotlin.internal.interop.RealmCoreMaximumFileSizeExceededException
import io.realm.kotlin.internal.interop.RealmCoreMissingPrimaryKeyException
import io.realm.kotlin.internal.interop.RealmCoreMissingPropertyValueException
import io.realm.kotlin.internal.interop.RealmCoreModifyPrimaryKeyException
import io.realm.kotlin.internal.interop.RealmCoreMultipleSyncAgentsException
import io.realm.kotlin.internal.interop.RealmCoreNoSuchObjectException
import io.realm.kotlin.internal.interop.RealmCoreNoSuchTableException
import io.realm.kotlin.internal.interop.RealmCoreNoneException
import io.realm.kotlin.internal.interop.RealmCoreNotClonableException
import io.realm.kotlin.internal.interop.RealmCoreNotInATransactionException
import io.realm.kotlin.internal.interop.RealmCoreOtherException
import io.realm.kotlin.internal.interop.RealmCoreOutOfDiskSpaceException
import io.realm.kotlin.internal.interop.RealmCoreOutOfMemoryException
import io.realm.kotlin.internal.interop.RealmCorePropertyNotNullableException
import io.realm.kotlin.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.kotlin.internal.interop.RealmCoreReadOnlyPropertyException
import io.realm.kotlin.internal.interop.RealmCoreSerializationErrorException
import io.realm.kotlin.internal.interop.RealmCoreUnexpectedPrimaryKeyException
import io.realm.kotlin.internal.interop.RealmCoreUnknownException
import io.realm.kotlin.internal.interop.RealmCoreUnsupportedFileFormatVersionException
import io.realm.kotlin.internal.interop.RealmCoreWrongPrimaryKeyTypeException
import io.realm.kotlin.internal.interop.RealmCoreWrongThreadException

/**
 * This class is a work-around for `cinterop` not being able to access `library-base` and
 * `library-sync` types, which is e.g. problematic when it comes to exceptions which break the
 * normal event flow.
 *
 * This class works around this by providing a way for `library-base` to install delegates
 * in `cinterop`. Then `cinterop` can use it to the public API to map internal errors when needed.
 * This works, but require that we can install the delegate at an appropriate time.
 *
 * Such a single point in time doesn't really exist as we don't have a public `Realm.init()` like
 * in Realm Java, so instead we need to make sure that all API entry points initializes this
 * class before touching any native code.
 *
 * With the current API, these entry points could be narrowed down to configuration classes
 * since they are prerequisite for interacting with any other Realm API
 *
 * - Configuration (for both RealmConfiguration and SyncConfiguration)
 * - AppConfiguration
 *
 * In theory, it it possible to start using our types: `RealmInstant`, `ObjectId`, etc., but before
 * a Realm is opened all of these will go through _unmanaged_ code paths so should be safe.
 *
 * @see io.realm.kotlin.internal.interop.CoreErrorConverterobject
 */
public object RealmInteropBridge {

    /**
     * This must be called before any calls to `io.realm.kotlin.internal.interop.RealmInterop`.
     * Failing to do so will result in unspecified behaviour.
     */
    public fun initialize() {
        CoreExceptionConverter.initialize()
    }
}

/**
 * Class for mapping between core exception types and public exception types. This works in two
 * ways:
 *
 * 1. It installs a delegate in `cinterop`, allowing `cinterop` to delegate the mapping if types
 *    to `library-base`, which have access to the public API types.
 *
 * 2. It exposes a method `CoreExceptionConverter.convertToPublicException` which allows code in
 *    `library-base` to convert exceptions from cinterop to something more appropriate. The reason
 *     being that sometimes cinterop doesn't have the full context in terms of what exception to
 *     throw, so using this method, allows specific methods to replace the exception being thrown.
 *     Like throwing `IllegalArgumentException` instead of `RealmException`.
 */
public object CoreExceptionConverter {

    public fun initialize() {
        // Just wrap all core exceptions in a public RealmException for now, we should be able t
        // throw subclasses of this without i being a breaking change.
        CoreErrorConverter.initialize { coreException: RealmCoreException ->
            RealmException(coreException.message ?: "", coreException)
        }
    }

    /**
     * Convert internal exceptions to more appropriate public types.
     * This is a hacky work-around until we get improved error handling from Core.
     */
    public fun convertToPublicException(exception: Throwable, customMessage: String = "", customize: ((RealmCoreException) -> Throwable?)? = null): Throwable {
        // Since we don't control exactly which exceptions end up here, we just care about mapping
        // exceptions that has been through the converter, ie. they are `RealmException` with
        // a `RealmCoreException` as cause. These we attempt to handle, all other exceptions get
        // passed through unchanged.
        if (exception is RealmException) {
            val cause: Throwable? = exception.cause
            if (cause is RealmCoreException) {
                var publicException: Throwable? = if (customize != null) customize(cause) else null
                if (publicException == null) {
                    publicException = genericRealmCoreExceptionHandler(customMessage, cause)
                }
                return publicException
            }
        }
        return exception
    }

    // Attempt to map Core exception types to a more reasonable public error
    private fun genericRealmCoreExceptionHandler(message: String, cause: RealmCoreException): Throwable {
        return when (cause) {
            is RealmCoreOutOfMemoryException,
            is RealmCoreUnsupportedFileFormatVersionException,
            is RealmCoreInvalidPathErrorException,
            is RealmCoreMultipleSyncAgentsException,
            is RealmCoreAddressSpaceExhaustedException,
            is RealmCoreMaximumFileSizeExceededException,
            is RealmCoreOutOfDiskSpaceException -> RealmException("$message: RealmCoreException(${cause.message})", cause)
            is RealmCoreIndexOutOfBoundsException -> IndexOutOfBoundsException("$message: RealmCoreException(${cause.message})")
            is RealmCoreInvalidArgumentException,
            is RealmCoreInvalidQueryStringException,
            is RealmCoreOtherException,
            is RealmCoreInvalidQueryException,
            is RealmCoreMissingPrimaryKeyException,
            is RealmCoreUnexpectedPrimaryKeyException,
            is RealmCoreWrongPrimaryKeyTypeException,
            is RealmCoreModifyPrimaryKeyException,
            is RealmCorePropertyNotNullableException,
            is RealmCoreDuplicatePrimaryKeyValueException -> IllegalArgumentException("$message: RealmCoreException(${cause.message})", cause)
            is RealmCoreNotInATransactionException,
            is RealmCoreDeleteOpenRealmException,
            is RealmCoreFileAccessErrorException,
            is RealmCoreFilePermissionDeniedException,
            is RealmCoreLogicException -> IllegalStateException("$message: RealmCoreException(${cause.message})", cause)
            is RealmCoreNoneException,
            is RealmCoreUnknownException,
            is RealmCoreNotClonableException,
            is RealmCoreWrongThreadException,
            is RealmCoreInvalidatedObjectException,
            is RealmCoreInvalidPropertyException,
            is RealmCoreMissingPropertyValueException,
            is RealmCorePropertyTypeMismatchException,
            is RealmCoreReadOnlyPropertyException,
            is RealmCoreNoSuchTableException,
            is RealmCoreNoSuchObjectException,
            is RealmCoreCrossTableLinkTargetException,
            is RealmCoreKeyNotFoundException,
            is RealmCoreColumnNotFoundException,
            is RealmCoreColumnAlreadyExistsException,
            is RealmCoreKeyAlreadyUsedException,
            is RealmCoreSerializationErrorException,
            is RealmCoreIllegalOperationException,
            is RealmCoreCallbackException -> RealmException("$message: RealmCoreException(${cause.message})", cause)
        }
    }
}
