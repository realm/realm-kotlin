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

import io.realm.kotlin.BaseRealm
import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.VersionId
import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.ext.isValid
import io.realm.kotlin.internal.RealmObjectHelper.assign
import io.realm.kotlin.internal.RealmObjectHelper.assignValuesOnUnmanagedObject
import io.realm.kotlin.internal.dynamic.DynamicUnmanagedRealmObject
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.ObjectKey
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmValue
import io.realm.kotlin.internal.platform.realmObjectCompanionOrThrow
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

// This cache is only valid for unmanaged realm objects as, for them we only consider the users
// `equals` method, which in general just is the memory address of the object.
internal typealias UnmanagedToManagedObjectCache = MutableMap<BaseRealmObject, BaseRealmObject> // Map<OriginalUnmanagedObject, CachedManagedObject>

// For managed realm objects we use `<ClassKey, ObjectKey, Version>` as a unique identifier
// We are using a hash on the Kotlin side so we can use a HashMap for O(1) lookup rather than
// having to do 0(n) filter with a JNI call for `realm_equals` for each element.
internal typealias RealmObjectIdentifier = Triple<ClassKey, ObjectKey, VersionId>
internal typealias ManagedToUnmanagedObjectCache = MutableMap<RealmObjectIdentifier, BaseRealmObject>

/**
 * Exception that can be thrown if there is a situation where we except certain interfaces to be
 * present, because they should have been added by the compiler plugin. But they where not.
 */
public val MISSING_PLUGIN: Throwable = IllegalStateException(
    "This class has not been modified " +
        "by the Realm Compiler Plugin. Has the Realm Gradle Plugin been applied to the project " +
        "with this model class?"
)

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
    cache: UnmanagedToManagedObjectCache = mutableMapOf(),
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

@Suppress("NestedBlockDepth", "LongMethod", "ComplexMethod", "UNCHECKED_CAST")
internal fun <T : BaseRealmObject> createDetachedCopy(
    mediator: Mediator,
    realmObject: T,
    currentDepth: Int,
    maxDepth: Int,
    cache: ManagedToUnmanagedObjectCache,
): T {
    val id = realmObject.getIdentifier()
    return cache[id] as T? ?: run {
        val unmanagedObject = mediator.companionOf(realmObject::class).`io_realm_kotlin_newInstance`() as BaseRealmObject
        cache[id] = unmanagedObject
        assignValuesOnUnmanagedObject(unmanagedObject, realmObject, mediator, currentDepth, maxDepth, cache)
        unmanagedObject
    } as T
}

/**
 * Work-around for Realms not being available inside RealmObjects until
 * https://github.com/realm/realm-kotlin/issues/582 is fixed.
 *
 * Note, due to Realm instances being shared across many threads, no guarantees are given for
 * the state of the Realm between calling this method and using it. I.e. it might either have
 * advanced or been closed.
 *
 * Given that this method can be called from multiple places, e.g. inside and outside write
 * transactions, the given Realm type must be provided by the caller as a generic argument.
 *
 * If a wrong type is provided a `ClassCastException` is thrown.
 *
 * If the object is unmanaged, `null` is returned. Error handling is left up to the caller.
 */
public fun <T : BaseRealm> RealmObject.getRealm(): T? {
    if (!this.isManaged()) {
        return null
    }
    return if (this is RealmObjectInternal) {
        val objRef: RealmObjectReference<out BaseRealmObject> = io_realm_kotlin_objectReference!!
        objRef.owner.owner as T
    } else {
        throw MISSING_PLUGIN
    }
}
public fun <T : BaseRealm> EmbeddedRealmObject.getRealm(): T? {
    if (!this.isManaged()) {
        return null
    }
    return if (this is RealmObjectInternal) {
        val objRef: RealmObjectReference<out BaseRealmObject> = this.io_realm_kotlin_objectReference!!
        objRef.owner.owner as T
    } else {
        throw MISSING_PLUGIN
    }
}
public fun <T : BaseRealm> RealmList<*>.getRealm(): T? {
    return when (this) {
        is UnmanagedRealmList -> null
        is ManagedRealmList -> {
            return this.operator.realmReference.owner as T
        }
        else -> {
            TODO("Unsupported list type: ${this::class}")
        }
    }
}
public fun <T : BaseRealm> RealmSet<*>.getRealm(): T? {
    return when (this) {
        is UnmanagedRealmSet -> null
        is ManagedRealmSet -> {
            return this.operator.realmReference.owner as T
        }
        else -> {
            TODO("Unsupported set type: ${this::class}")
        }
    }
}
public fun <T : BaseRealm> RealmResults<*>.getRealm(): T? {
    if (this is RealmResultsImpl) {
        realm.checkClosed()
        return this.realm.owner as T
    } else {
        throw IllegalStateException("Unsupported class: $this::class")
    }
}
