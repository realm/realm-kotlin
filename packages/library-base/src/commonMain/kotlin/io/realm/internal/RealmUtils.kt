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

package io.realm.internal

import io.realm.MutableRealm
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.internal.interop.RealmCoreAddressSpaceExhaustedException
import io.realm.internal.interop.RealmCoreCallbackException
import io.realm.internal.interop.RealmCoreColumnAlreadyExistsException
import io.realm.internal.interop.RealmCoreColumnNotFoundException
import io.realm.internal.interop.RealmCoreCrossTableLinkTargetException
import io.realm.internal.interop.RealmCoreDeleteOpenRealmException
import io.realm.internal.interop.RealmCoreDuplicatePrimaryKeyValueException
import io.realm.internal.interop.RealmCoreException
import io.realm.internal.interop.RealmCoreFileAccessErrorException
import io.realm.internal.interop.RealmCoreFilePermissionDeniedException
import io.realm.internal.interop.RealmCoreIndexOutOfBoundsException
import io.realm.internal.interop.RealmCoreInvalidArgumentException
import io.realm.internal.interop.RealmCoreInvalidPathErrorException
import io.realm.internal.interop.RealmCoreInvalidPropertyException
import io.realm.internal.interop.RealmCoreInvalidQueryException
import io.realm.internal.interop.RealmCoreInvalidQueryStringException
import io.realm.internal.interop.RealmCoreInvalidatedObjectException
import io.realm.internal.interop.RealmCoreKeyAlreadyUsedException
import io.realm.internal.interop.RealmCoreKeyNotFoundException
import io.realm.internal.interop.RealmCoreLogicException
import io.realm.internal.interop.RealmCoreMaximumFileSizeExceededException
import io.realm.internal.interop.RealmCoreMissingPrimaryKeyException
import io.realm.internal.interop.RealmCoreMissingPropertyValueException
import io.realm.internal.interop.RealmCoreModifyPrimaryKeyException
import io.realm.internal.interop.RealmCoreMultipleSyncAgentsException
import io.realm.internal.interop.RealmCoreNoSuchObjectException
import io.realm.internal.interop.RealmCoreNoSuchTableException
import io.realm.internal.interop.RealmCoreNoneException
import io.realm.internal.interop.RealmCoreNotClonableException
import io.realm.internal.interop.RealmCoreNotInATransactionException
import io.realm.internal.interop.RealmCoreOtherException
import io.realm.internal.interop.RealmCoreOutOfDiskSpaceException
import io.realm.internal.interop.RealmCoreOutOfMemoryException
import io.realm.internal.interop.RealmCorePropertyNotNullableException
import io.realm.internal.interop.RealmCorePropertyTypeMismatchException
import io.realm.internal.interop.RealmCoreReadOnlyPropertyException
import io.realm.internal.interop.RealmCoreSerializationErrorException
import io.realm.internal.interop.RealmCoreUnexpectedPrimaryKeyException
import io.realm.internal.interop.RealmCoreUnknownException
import io.realm.internal.interop.RealmCoreUnsupportedFileFormatVersionException
import io.realm.internal.interop.RealmCoreWrongPrimaryKeyTypeException
import io.realm.internal.interop.RealmCoreWrongThreadException
import io.realm.internal.interop.RealmInterop
import io.realm.internal.platform.realmObjectCompanionOrThrow
import io.realm.isManaged
import io.realm.isValid
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KProperty1

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

internal fun <T : RealmObject> create(mediator: Mediator, realm: RealmReference, type: KClass<T>): T =
    create(mediator, realm, type, io.realm.internal.platform.realmObjectCompanionOrThrow(type).`$realm$className`)

internal fun <T : RealmObject> create(mediator: Mediator, realm: RealmReference, type: KClass<T>, className: String): T {
    try {
        val managedModel = mediator.createInstanceOf(type)
        val key = realm.schemaMetadata.getOrThrow(className).classKey
        key?.let {
            return managedModel.manage(
                realm,
                mediator,
                type,
                RealmInterop.realm_object_create(realm.dbPointer, key)
            )
        } ?: throw IllegalArgumentException("Schema doesn't include class '$className'")
    } catch (e: RealmCoreException) {
        throw genericRealmCoreExceptionHandler("Failed to create object of type '$className'", e)
    }
}

internal fun <T : RealmObject> create(
    mediator: Mediator,
    realm: RealmReference,
    type: KClass<T>,
    primaryKey: Any?,
    updatePolicy: MutableRealm.UpdatePolicy
): T = create(
    mediator,
    realm,
    type,
    realmObjectCompanionOrThrow(type).`$realm$className`,
    primaryKey,
    updatePolicy
)

@Suppress("LongParameterList")
internal fun <T : RealmObject> create(
    mediator: Mediator,
    realm: RealmReference,
    type: KClass<T>,
    className: String,
    primaryKey: Any?,
    updatePolicy: MutableRealm.UpdatePolicy
): T {
    try {
        val key = realm.schemaMetadata.getOrThrow(className).classKey
        key?.let {
            val managedModel = mediator.createInstanceOf(type)
            val nativeObject = when (updatePolicy) {
                MutableRealm.UpdatePolicy.ERROR -> {
                    RealmInterop.realm_object_create_with_primary_key(
                        realm.dbPointer,
                        key,
                        primaryKey
                    )
                }
                MutableRealm.UpdatePolicy.ALL -> {
                    RealmInterop.realm_object_get_or_create_with_primary_key(
                        realm.dbPointer,
                        key,
                        primaryKey
                    )
                }
            }
            return managedModel.manage(realm, mediator, type, nativeObject)
        } ?: error("Couldn't find key for class $className")
    } catch (e: RealmCoreException) {
        throw genericRealmCoreExceptionHandler("Failed to create object of type '$className'", e)
    }
}

@Suppress("NestedBlockDepth")
internal fun <T> copyToRealm(
    mediator: Mediator,
    realmReference: RealmReference,
    element: T,
    updatePolicy: MutableRealm.UpdatePolicy = MutableRealm.UpdatePolicy.ERROR,
    cache: MutableMap<RealmObjectInternal, RealmObjectInternal> = mutableMapOf(),
): T {
    return if (element is RealmObjectInternal) {
        // Throw if object is not valid
        if (!element.isValid()) {
            throw IllegalArgumentException("Cannot copy an invalid managed object to Realm.")
        }

        if (element.isManaged()) {
            if (element.`$realm$Owner` == realmReference) {
                element
            } else {
                throw IllegalArgumentException("Cannot set/copyToRealm an outdated object. User findLatest(object) to resolve the latest version in the given context.")
            }
        } else {
            // Copy object if it is not managed
            val instance: RealmObjectInternal = element
            val companion = mediator.companionOf(instance::class)
            @Suppress("UNCHECKED_CAST")
            val members = companion.`$realm$fields` as List<KMutableProperty1<RealmObjectInternal, Any?>>
            val primaryKeyProperty = companion.`$realm$primaryKey`
            val target = primaryKeyProperty?.let { primaryKey ->
                @Suppress("UNCHECKED_CAST")
                create(
                    mediator,
                    realmReference,
                    instance::class,
                    (primaryKey as KProperty1<RealmObjectInternal, Any?>).get(instance),
                    updatePolicy
                )
            } ?: create(mediator, realmReference, instance::class)

            cache[instance] = target

            // TODO OPTIMIZE We could set all properties at once with on C-API call
            for (member: KMutableProperty1<RealmObjectInternal, Any?> in members) {
                val targetValue = member.get(instance).let { sourceObject ->
                    // Check whether the source is a RealmObject, a primitive or a list
                    // In case of list ensure the values from the source are passed to the native list
                    if (sourceObject is RealmObjectInternal && !sourceObject.`$realm$IsManaged`) {
                        cache.getOrPut(sourceObject) {
                            copyToRealm(mediator, realmReference, sourceObject, updatePolicy, cache)
                        }
                    } else if (sourceObject is RealmList<*>) {
                        processListMember(
                            mediator,
                            realmReference,
                            cache,
                            member,
                            target,
                            sourceObject
                        )
                    } else {
                        sourceObject
                    }
                }
                targetValue?.let {
                    // TODO OPTIMIZE Should we do a separate setter that allows the isDefault flag for sync
                    //  optimizations
                    member.set(target, it)
                }
            }
            @Suppress("UNCHECKED_CAST")
            target as T
        }
    } else {
        // Ignore copy if the element is of a primitive type
        element
    }
}

@Suppress("LongParameterList")
private fun <T : RealmObject> processListMember(
    mediator: Mediator,
    realmPointer: RealmReference,
    cache: MutableMap<RealmObjectInternal, RealmObjectInternal>,
    member: KMutableProperty1<T, Any?>,
    target: T,
    sourceObject: RealmList<*>
): RealmList<Any?> {
    @Suppress("UNCHECKED_CAST")
    val list = member.get(target) as RealmList<Any?>
    for (item in sourceObject) {
        // Same as in copyToRealm, check whether we are working with a primitive or a RealmObject
        if (item is RealmObjectInternal && !item.`$realm$IsManaged`) {
            val value = cache.getOrPut(item) {
                copyToRealm(mediator, realmPointer, item, MutableRealm.UpdatePolicy.ERROR, cache)
            }
            list.add(value)
        } else {
            list.add(item)
        }
    }
    return list
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
        is RealmCoreDuplicatePrimaryKeyValueException -> IllegalArgumentException("$message: RealmCoreException(${cause.message})", cause)
        is RealmCoreNotInATransactionException,
        is RealmCoreDeleteOpenRealmException,
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
        is RealmCorePropertyNotNullableException,
        is RealmCoreNoSuchTableException,
        is RealmCoreNoSuchObjectException,
        is RealmCoreCrossTableLinkTargetException,
        is RealmCoreKeyNotFoundException,
        is RealmCoreColumnNotFoundException,
        is RealmCoreColumnAlreadyExistsException,
        is RealmCoreKeyAlreadyUsedException,
        is RealmCoreSerializationErrorException,
        is RealmCoreCallbackException -> RuntimeException("$message: RealmCoreException(${cause.message})", cause)
    }
}
