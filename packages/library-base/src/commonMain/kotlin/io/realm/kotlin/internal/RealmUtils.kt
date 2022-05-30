/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use th
 * is file except in compliance with the License.
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

import io.realm.kotlin.BaseRealmObject
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.RealmObjectHelper.assign
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.PropertyKey
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
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.isValid
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal typealias ObjectCache = MutableMap<BaseRealmObject, BaseRealmObject>

/**
 * Add a check and error message for code that never be reached because it should have been
 * replaced by the Compiler Plugin.
 */
@Suppress("FunctionNaming", "NOTHING_TO_INLINE")
internal inline fun REPLACED_BY_IR(
    message: String = "This code should have been replaced by the Realm Compiler Plugin. " +
        "Has the `realm-kotlin` Gradle plugin been applied to the project?"
): Nothing = throw AssertionError(message)

internal fun checkRealmClosed(realm: RealmReference) {
    if (RealmInterop.realm_is_closed(realm.dbPointer)) {
        throw IllegalStateException("Realm has been closed and is no longer accessible: ${realm.owner.configuration.path}")
    }
}

internal fun <T : BaseRealmObject> create(mediator: Mediator, realm: LiveRealmReference, type: KClass<T>): T =
    create(mediator, realm, type, realmObjectCompanionOrThrow(type).`io_realm_kotlin_className`)

internal fun <T : BaseRealmObject> create(mediator: Mediator, realm: LiveRealmReference, type: KClass<T>, className: String): T {
    try {
        val key = realm.schemaMetadata.getOrThrow(className).classKey
        return key?.let {
            RealmInterop.realm_object_create(realm.dbPointer, key).toRealmObject(
                realm = realm,
                mediator = mediator,
                clazz = type,
            )
        } ?: throw IllegalArgumentException("Schema doesn't include class '$className'")
    } catch (e: RealmCoreException) {
        throw genericRealmCoreExceptionHandler("Failed to create object of type '$className'", e)
    }
}

@Suppress("LongParameterList")
internal fun <T : BaseRealmObject> create(
    mediator: Mediator,
    realm: LiveRealmReference,
    type: KClass<T>,
    className: String,
    primaryKey: RealmValue,
    updatePolicy: UpdatePolicy
): T {
    try {
        val key = realm.schemaMetadata.getOrThrow(className).classKey
        return key?.let {
            when (updatePolicy) {
                UpdatePolicy.ERROR -> {
                    RealmInterop.realm_object_create_with_primary_key(
                        realm.dbPointer,
                        key,
                        primaryKey
                    )
                }
                UpdatePolicy.ALL -> {
                    RealmInterop.realm_object_get_or_create_with_primary_key(
                        realm.dbPointer,
                        key,
                        primaryKey
                    )
                }
            }.toRealmObject(
                realm = realm,
                mediator = mediator,
                clazz = type,
            )
        } ?: error("Couldn't find key for class $className")
    } catch (e: RealmCoreException) {
        throw genericRealmCoreExceptionHandler("Failed to create object of type '$className'", e)
    }
}

@Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod")
internal fun <T : BaseRealmObject> copyToRealm(
    mediator: Mediator,
    realmReference: LiveRealmReference,
    element: T,
    updatePolicy: UpdatePolicy = UpdatePolicy.ERROR,
    cache: ObjectCache = mutableMapOf(),
): T {
    // Throw if object is not valid
    if (!element.isValid()) {
        throw IllegalArgumentException("Cannot copy an invalid managed object to Realm.")
    }

    return cache[element] as T? ?: element.runIfManaged {
        if (owner == realmReference) {
            element
        } else {
            throw IllegalArgumentException("Cannot set/copyToRealm an outdated object. User findLatest(object) to find the version of the object required in the given context.")
        }
    } ?: run {
        // Create a new object if it wasn't managed
        var className: String?
        var hasPrimaryKey: Boolean = false
        var primaryKey: Any? = null
        if (element is DynamicUnmanagedRealmObject) {
            className = element.type
            val primaryKeyName: String? =
                realmReference.schemaMetadata[className]?.let { classMetaData ->
                    if (classMetaData.isEmbeddedRealmObject) {
                        throw IllegalArgumentException("Cannot create embedded object without a parent")
                    }
                    classMetaData.primaryKeyProperty?.key?.let { key: PropertyKey ->
                        classMetaData.get(key)?.name
                    }
                }
            hasPrimaryKey = primaryKeyName != null
            primaryKey = primaryKeyName?.let {
                val properties = element.properties
                if (properties.containsKey(primaryKeyName)) {
                    properties.get(primaryKeyName)
                } else {
                    throw IllegalArgumentException("Cannot create object of type '$className' without primary key property '$primaryKeyName'")
                }
            }
        } else {
            val companion = realmObjectCompanionOrThrow(element::class)
            className = companion.io_realm_kotlin_className
            if (companion.io_realm_kotlin_isEmbedded) {
                throw IllegalArgumentException("Cannot create embedded object without a parent")
            }
            companion.`io_realm_kotlin_primaryKey`?.let {
                hasPrimaryKey = true
                primaryKey = (it as KProperty1<BaseRealmObject, Any?>).get(element)
            }
        }
        val target = if (hasPrimaryKey) {
            @Suppress("UNCHECKED_CAST")
            create(
                mediator,
                realmReference,
                element::class,
                className,
                RealmValueArgumentConverter.convertArg(primaryKey),
                updatePolicy
            )
        } else {
            create(mediator, realmReference, element::class, className)
        }

        cache[element] = target
        assign(target, element, updatePolicy, cache)
        target
    } as T
}

internal fun genericRealmCoreExceptionHandler(message: String, cause: RealmCoreException): Throwable {
    return when (cause) {
        is RealmCoreOutOfMemoryException,
        is RealmCoreUnsupportedFileFormatVersionException,
        is RealmCoreInvalidPathErrorException,
        is RealmCoreMultipleSyncAgentsException,
        is RealmCoreAddressSpaceExhaustedException,
        is RealmCoreMaximumFileSizeExceededException,
        is RealmCoreOutOfDiskSpaceException -> Error("$message: RealmCoreException(${cause.message})", cause)
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
        is RealmCoreCallbackException -> RuntimeException("$message: RealmCoreException(${cause.message})", cause)
    }
}
