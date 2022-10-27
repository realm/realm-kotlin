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

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.internal.RealmObjectHelper.assign
import io.realm.kotlin.internal.RealmObjectHelper.assignTypedOnUnmanagedObject
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

internal typealias ObjectCache = MutableMap<BaseRealmObject, BaseRealmObject> // Map<OriginalObject, CachedObject>

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
    } catch (e: Throwable) {
        throw CoreExceptionConverter.convertToPublicException(
            e,
            "Failed to create object of type '$className'"
        )
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
    } catch (e: Throwable) {
        throw CoreExceptionConverter.convertToPublicException(
            e,
            "Failed to create object of type '$className'"
        )
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
            throw IllegalArgumentException("Cannot set/copyToRealm an outdated object. Use findLatest(object) to find the version of the object required in the given context.")
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

@Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod")
internal fun <T : BaseRealmObject> createDetachedCopy(
    mediator: Mediator,
    realmReference: RealmReference,
    element: T,
    depth: Int,
    cache: ObjectCache = mutableMapOf(),
): T {
    // Throw if object is not valid
    if (!element.isManaged()) {
        throw IllegalArgumentException("Cannot copy an unmanaged object from Realm.")
    }
    if (!element.isValid()) {
        throw IllegalArgumentException("Cannot copy an invalid managed object from Realm.")
    }

    // TODO Check if already in case
    return cache[element] as T? // ?: element.runIfManaged {
//        if (owner == realmReference) {
//            element
//        } else {
//            throw IllegalArgumentException("Cannot set/copyToRealm an outdated object. Use findLatest(object) to find the version of the object required in the given context.")
//        }
/*    }*/ ?: run {
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

        val target = mediator.companionOf(element::class).`io_realm_kotlin_newInstance`() as BaseRealmObject
        cache[element] = target
        assignTypedOnUnmanagedObject(target, element, mediator, depth, cache)
        target
    } as T
}
