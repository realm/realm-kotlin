/*
 * Copyright 2021 Realm Inc.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.realm.kotlin.test.util

import io.realm.kotlin.ObjectId
import io.realm.kotlin.RealmInstant
import io.realm.kotlin.RealmObject
import io.realm.kotlin.internal.interop.CollectionType
import io.realm.kotlin.internal.interop.PropertyType
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType

public object TypeDescriptor {

    // Core field types with their support level
    @Suppress("LongParameterList")
    enum class CoreFieldType(
        val type: PropertyType,
        val nullable: Boolean, // TODO this doesn't contain enough info for lists
        val nonNullable: Boolean, // TODO this doesn't contain enough info for lists
        val listSupport: Boolean,
        val primaryKeySupport: Boolean,
        val indexSupport: Boolean,
        val canBeNull: Set<CollectionType>, // favor using this over "nullable"
        val canBeNotNull: Set<CollectionType> // favor using this over "nonNullable"
    ) {
        INT(
            type = PropertyType.RLM_PROPERTY_TYPE_INT,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        BOOL(
            type = PropertyType.RLM_PROPERTY_TYPE_BOOL,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        STRING(
            type = PropertyType.RLM_PROPERTY_TYPE_STRING,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        OBJECT(
            type = PropertyType.RLM_PROPERTY_TYPE_OBJECT,
            nullable = true,
            nonNullable = false,
            listSupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            canBeNull = nullabilityForAll.toMutableSet().apply {
                remove(CollectionType.RLM_COLLECTION_TYPE_LIST)
            },
            canBeNotNull = nullabilityForAll
        ),
        FLOAT(
            type = PropertyType.RLM_PROPERTY_TYPE_FLOAT,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        DOUBLE(
            type = PropertyType.RLM_PROPERTY_TYPE_DOUBLE,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = false,
            indexSupport = false,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        TIMESTAMP(
            type = PropertyType.RLM_PROPERTY_TYPE_TIMESTAMP,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = false,
            indexSupport = true,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        ),
        OBJECT_ID(
            type = PropertyType.RLM_PROPERTY_TYPE_OBJECT_ID,
            nullable = true,
            nonNullable = true,
            listSupport = true,
            primaryKeySupport = true,
            indexSupport = true,
            canBeNull = nullabilityForAll,
            canBeNotNull = nullabilityForAll
        );
    }

    private val nullabilityForAll: Set<CollectionType> = setOf(
        CollectionType.RLM_COLLECTION_TYPE_NONE,
        CollectionType.RLM_COLLECTION_TYPE_LIST,
        CollectionType.RLM_COLLECTION_TYPE_SET,
        CollectionType.RLM_COLLECTION_TYPE_DICTIONARY
    )

    // Classifiers for types that can be used in aggregate queries
    val aggregateClassifiers: Map<KClassifier, CoreFieldType> = mapOf(
        Byte::class to CoreFieldType.INT,
        Char::class to CoreFieldType.INT,
        Short::class to CoreFieldType.INT,
        Int::class to CoreFieldType.INT,
        Long::class to CoreFieldType.INT,
        Float::class to CoreFieldType.FLOAT,
        Double::class to CoreFieldType.DOUBLE
    )

    // Kotlin classifier to Core field type mappings
    val classifiers: Map<KClassifier, CoreFieldType> = aggregateClassifiers + mapOf(
        Boolean::class to CoreFieldType.BOOL,
        String::class to CoreFieldType.STRING,
        RealmInstant::class to CoreFieldType.TIMESTAMP,
        ObjectId::class to CoreFieldType.OBJECT_ID,
        RealmObject::class to CoreFieldType.OBJECT
    )

    // Element type is the type of the element of either a singular field or the container element type.
    // Basically just a clone of KType but with the ability to create them from input parameters at
    // runtime as KClassifier.createType is not available for Kotlin Native.
    data class ElementType(val classifier: KClassifier, val nullable: Boolean) {
        val realmFieldType = classifiers[classifier] ?: throw TODO("$classifier")

        override fun toString(): String {
            return "RType(${"${(classifier as KClass<*>).simpleName}"}${if (nullable) "?" else ""})"
        }
    }

    // Utility method to generate cartesian product of classifiers and nullability values according
    // to the support level of the underlying core field type specified in CoreFieldType.
    fun elementTypes(
        classifiers: Collection<KClassifier>,
    ): MutableSet<ElementType> {
        return classifiers.fold(
            mutableSetOf<ElementType>(),
            { acc, classifier ->
                val realmFieldType = TypeDescriptor.classifiers[classifier]
                    ?: error("Unmapped classifier $classifier")
                if (realmFieldType.nullable) {
                    acc.add(ElementType(classifier, true))
                }
                if (realmFieldType.nonNullable) {
                    acc.add(ElementType(classifier, false))
                }
                acc
            }
        )
    }

    fun elementTypesForList(
        classifiers: Collection<KClassifier>,
    ): MutableSet<ElementType> {
        return classifiers.fold(
            mutableSetOf<ElementType>(),
            { acc, classifier ->
                val realmFieldType = TypeDescriptor.classifiers[classifier]
                    ?: error("Unmapped classifier $classifier")
                if (realmFieldType.canBeNull.contains(CollectionType.RLM_COLLECTION_TYPE_LIST)) {
                    acc.add(ElementType(classifier, true))
                }
                if (realmFieldType.canBeNotNull.contains(CollectionType.RLM_COLLECTION_TYPE_LIST)) {
                    acc.add(ElementType(classifier, false))
                }
                acc
            }
        )
    }

    // Convenience variables holding collections of the various supported types
    val elementClassifiers: Set<KClassifier> = classifiers.keys
    val elementTypes = elementTypes(elementClassifiers)
    val elementTypesForList = elementTypesForList(elementClassifiers)

    // Convenience variables holding collection of various groups of Realm field types
    val allSingularFieldTypes = elementTypes.map {
        RealmFieldType(CollectionType.RLM_COLLECTION_TYPE_NONE, it)
    }
    val allListFieldTypes = elementTypesForList.filter {
        it.realmFieldType.listSupport
    }.map {
        RealmFieldType(CollectionType.RLM_COLLECTION_TYPE_LIST, it)
    }
    // TODO Set
    // TODO Dict
    val allFieldTypes: List<RealmFieldType> = allSingularFieldTypes + allListFieldTypes
    val allPrimaryKeyFieldTypes = allFieldTypes.filter { it.isPrimaryKeySupported }

    // Realm field type represents the type of a given user specified field in the RealmObject
    data class RealmFieldType(
        val collectionType: CollectionType,
        val elementType: ElementType
    ) {
        val isPrimaryKeySupported: Boolean =
            collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE && elementType.realmFieldType.primaryKeySupport
        val isIndexingSupported: Boolean =
            collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE && elementType.realmFieldType.indexSupport

        // Utility method to generate Kotlin code for the specific field
        fun toKotlinLiteral(): String {
            val element =
                (elementType.classifier as KClass<*>).simpleName + (if (elementType.nullable) "?" else "")
            return when (collectionType) {
                CollectionType.RLM_COLLECTION_TYPE_NONE -> element
                CollectionType.RLM_COLLECTION_TYPE_LIST -> "List<$element>"
                CollectionType.RLM_COLLECTION_TYPE_SET -> TODO()
                CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> TODO()
                else -> throw IllegalArgumentException("Wrong collection type: $collectionType")
            }
        }

        override fun toString(): String {
            return "RType(collectionType=$collectionType, elementType=$elementType)"
        }
    }

    // Convenience methods to easily derive Realm field information from Kotlin types.
    fun KType.rType(): RealmFieldType {
        val elementType = elementType(this)
        return RealmFieldType(
            collectionType(this),
            ElementType(elementType.classifier!!, elementType.isMarkedNullable)
        )
    }

    fun KMutableProperty1<*, *>.rType(): RealmFieldType {
        // FIXME returnType isn't available in Common, we should create our custom type:
        //  https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/-k-callable/
        //  This only works if you specifically run Android or MacOS tests, running `assemble` crashes.
        return this.returnType.rType()
    }

    // Convenience class to easily derive information about a Realm field directly from the property.
    // It is unclear if we can derive sufficient information without access to annotations at runtime,
    // but alternatively we can maybe query information from the schema and key cache infrastructure.
    class RealmFieldDescriptor(val property: KMutableProperty1<*, *>) {
        val rType by lazy { property.rType() }

        val isElementNullable: Boolean = rType.elementType.nullable

        // TODO Annotations are not available at runtime on Kotlin native
        // val isPrimariKey: Boolean =
        //    rType.isPrimaryKeySupported && property.annotations.isNotEmpty() && property.annotations[0] is PrimaryKey

        // TODO Public/internal name. We cannot pull the public name for when obfuscated
    }

    private fun collectionType(type: KType): CollectionType {
        return when (type.classifier) {
            Set::class -> CollectionType.RLM_COLLECTION_TYPE_SET
            List::class -> CollectionType.RLM_COLLECTION_TYPE_LIST
            Map::class -> CollectionType.RLM_COLLECTION_TYPE_DICTIONARY
            else -> CollectionType.RLM_COLLECTION_TYPE_NONE
        }
    }

    private fun elementType(type: KType) = when (val collectionType = collectionType(type)) {
        CollectionType.RLM_COLLECTION_TYPE_NONE ->
            type
        CollectionType.RLM_COLLECTION_TYPE_SET,
        CollectionType.RLM_COLLECTION_TYPE_LIST ->
            type.arguments[0].type!!
        CollectionType.RLM_COLLECTION_TYPE_DICTIONARY ->
            type.arguments[1].type!!
        else -> throw IllegalArgumentException("Wrong collection type: $collectionType")
    }
}
