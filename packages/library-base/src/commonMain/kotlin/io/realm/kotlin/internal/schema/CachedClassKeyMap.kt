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

package io.realm.kotlin.internal.schema

import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyInfo
import io.realm.kotlin.internal.interop.PropertyKey
import io.realm.kotlin.internal.interop.PropertyType
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmPointer
import io.realm.kotlin.internal.interop.SCHEMA_NO_VALUE
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

/**
 * Schema metadata providing access to class metadata for the schema.
 */
public interface SchemaMetadata {
    public operator fun get(className: String): ClassMetadata?
    public operator fun get(classKey: ClassKey): ClassMetadata?
    public fun getOrThrow(className: String): ClassMetadata = this[className]
        ?: throw IllegalArgumentException("Schema does not contain a class named '$className'")
}

/**
 * Class metadata providing access class and property keys.
 */
public interface ClassMetadata {
    public val clazz: KClass<out TypedRealmObject>?
    public val className: String
    public val classKey: ClassKey
    public val properties: List<PropertyMetadata>
    public val primaryKeyProperty: PropertyMetadata?
    public val isEmbeddedRealmObject: Boolean
    public operator fun get(propertyName: String): PropertyMetadata?
    public operator fun get(propertyKey: PropertyKey): PropertyMetadata?
    public operator fun get(property: KProperty<*>): PropertyMetadata?
    public fun getOrThrow(propertyName: String): PropertyMetadata = this[propertyName]
        ?: throw IllegalArgumentException("Schema for type '$className' doesn't contain a property named '$propertyName'")
    /**
     * Returns `true` if this class has been defined by the user, `false` is returned
     * if this class is only found in the on-disk schema.
     */
    public fun isUserDefined(): Boolean = (clazz != null)
}

public interface PropertyMetadata {
    public val name: String
    public val publicName: String
    public val key: PropertyKey
    public val collectionType: CollectionType
    public val type: PropertyType
    public val isNullable: Boolean
    public val isPrimaryKey: Boolean
    public val accessor: KProperty1<BaseRealmObject, Any?>?
    public val linkTarget: String
    public val linkOriginPropertyName: String
    public val isComputed: Boolean
    /**
     * Returns `true` if this property has been defined by the user, `false` is returned
     * if this property is only found in the on-disk schema.
     */
    public fun isUserDefined(): Boolean = (accessor != null)
}

/**
 * Schema metadata implementation that postpones class key lookup until first access.
 *
 * The provided class metadata entries are `CachedClassMetadata` for which property keys are also
 * only looked up on first access.
 */
public class CachedSchemaMetadata(
    private val dbPointer: RealmPointer,
    companions: Collection<RealmObjectCompanion>
) : SchemaMetadata {
    // TODO OPTIMIZE We should theoretically be able to lazy load these, but it requires locking
    //  and 'by lazy' initializers can throw
    //  kotlin.native.concurrent.InvalidMutabilityException: Frozen during lazy computation
    private val classMapByName: Map<String, CachedClassMetadata>
    private val classMapByKey: Map<ClassKey, CachedClassMetadata>

    init {
        classMapByName = RealmInterop.realm_get_class_keys(dbPointer).map<ClassKey, Pair<String, CachedClassMetadata>> {
            val classInfo = RealmInterop.realm_get_class(dbPointer, it)
            // FIXME OPTIMIZE
            val className = classInfo.name
            val companion: RealmObjectCompanion? = companions.singleOrNull { it.io_realm_kotlin_className == className }
            className to CachedClassMetadata(dbPointer, className, classInfo.key, companion)
        }.toMap()

        classMapByKey = classMapByName.map { (_, metadata: CachedClassMetadata) ->
            metadata.classKey to metadata
        }.toMap()
    }

    override fun get(className: String): ClassMetadata? = classMapByName[className]
    override fun get(classKey: ClassKey): ClassMetadata? = classMapByKey[classKey]
}

/**
 * Class metadata implementation that provides a lazy loaded cache to property keys.
 */
public class CachedClassMetadata(
    dbPointer: RealmPointer,
    override val className: String,
    override val classKey: ClassKey,
    companion: RealmObjectCompanion?
) : ClassMetadata {
    // TODO OPTIMIZE We should theoretically be able to lazy load these, but it requires locking
    //  and 'by lazy' initializers can throw
    //  kotlin.native.concurrent.InvalidMutabilityException: Frozen during lazy computation
    override val properties: List<PropertyMetadata>
    private val propertyMap: Map<KProperty<*>?, PropertyMetadata>
    private val nameMap: Map<String, PropertyMetadata>
    private val keyMap: Map<PropertyKey, PropertyMetadata>

    override val primaryKeyProperty: PropertyMetadata?
    override val isEmbeddedRealmObject: Boolean
    override val clazz: KClass<out TypedRealmObject>? = companion?.io_realm_kotlin_class

    init {
        val classInfo = RealmInterop.realm_get_class(dbPointer, classKey)
        RealmInterop.realm_get_class_properties(
            dbPointer,
            classInfo.key,
            classInfo.numProperties + classInfo.numComputedProperties
        ).let { interopProperties ->
            properties = interopProperties.map { propertyInfo: PropertyInfo ->
                CachedPropertyMetadata(
                    propertyInfo,
                    companion?.io_realm_kotlin_fields?.get(propertyInfo.name)?.second
                )
            }
        }

        // TODO OPTIMIZE We should initialize this in one iteration
        primaryKeyProperty = properties.firstOrNull { it.isPrimaryKey }
        isEmbeddedRealmObject = classInfo.isEmbedded

        nameMap = properties.associateBy { it.name } + properties.filterNot { it.publicName == SCHEMA_NO_VALUE }.associateBy { it.publicName }
        keyMap = properties.associateBy { it.key }
        propertyMap = properties.associateBy { it.accessor }
    }

    override fun get(propertyName: String): PropertyMetadata? = nameMap[propertyName]
    override fun get(propertyKey: PropertyKey): PropertyMetadata? = keyMap[propertyKey]
    override fun get(property: KProperty<*>): PropertyMetadata? = propertyMap[property]
}

public class CachedPropertyMetadata(
    propertyInfo: PropertyInfo,
    override val accessor: KProperty1<BaseRealmObject, Any?>? = null
) : PropertyMetadata {
    override val name: String = propertyInfo.name
    override val publicName: String = propertyInfo.publicName
    override val key: PropertyKey = propertyInfo.key
    override val collectionType: CollectionType = propertyInfo.collectionType
    override val type: PropertyType = propertyInfo.type
    override val isNullable: Boolean = propertyInfo.isNullable
    override val isPrimaryKey: Boolean = propertyInfo.isPrimaryKey
    override val linkTarget: String = propertyInfo.linkTarget
    override val linkOriginPropertyName: String = propertyInfo.linkOriginPropertyName
    override val isComputed: Boolean = propertyInfo.isComputed
}
