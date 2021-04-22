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

package io.realm.internal

import io.realm.PrimaryKey
import io.realm.RealmObject
import io.realm.interop.CollectionType
import io.realm.interop.PropertyType
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// Core field types with their support level
enum class RealmFieldType(
    val type: PropertyType,
    val nullable: Boolean,
    val nonNullable: Boolean,
    val listSupport: Boolean,
    val setSupport: Boolean,
    val mapSupport: Boolean,
    val mixedSupport: Boolean,
    val primaryKeySupport: Boolean,
    val indexSupport: Boolean,
) {
    // FIXME Get the support level right
    INT(PropertyType.RLM_PROPERTY_TYPE_INT, true,  true, true, false, false, false, true, true),
    BOOL(PropertyType.RLM_PROPERTY_TYPE_BOOL, true, true, true, false, false, false, false, true),
    STRING(PropertyType.RLM_PROPERTY_TYPE_STRING, true, true, true, false, false, false, true, true),
    OBJECT(PropertyType.RLM_PROPERTY_TYPE_OBJECT, true, false, true, false, false, false, false, false),
    FLOAT(PropertyType.RLM_PROPERTY_TYPE_FLOAT, true, true, true, false, false, false, false, false),
    DOUBLE(PropertyType.RLM_PROPERTY_TYPE_DOUBLE, true, true, true, false, false, false, false, false);
}

// Kotlin classifier to Core field type mappings
val classifiers: Map<KClassifier, RealmFieldType> = mapOf(
    Byte::class to RealmFieldType.INT,
    Char::class to RealmFieldType.INT,
    Short::class to RealmFieldType.INT,
    Int::class to RealmFieldType.INT,
    Long::class to RealmFieldType.INT,
    Boolean::class to RealmFieldType.BOOL,
    Float::class to RealmFieldType.FLOAT,
    Double::class to RealmFieldType.DOUBLE,
    String::class to RealmFieldType.STRING,
    RealmObject::class to RealmFieldType.OBJECT
)

// Basically just a clone of KType but with the ability to create them from input parameters at
// runtime as KClassifier.createType is not available for Kotlin Native.
data class RElementType(val classifier: KClassifier, val nullable: Boolean) {

    val realmFieldType = classifiers[classifier] ?: throw TODO("$classifier")

    override fun toString(): String {
        return "RType(${"${(classifier as KClass<*>).simpleName}"}${if (nullable) "?" else ""})"
    }
}

// Utility method to generate cartesian product of classifiers and nullability values
fun elementTypes(
    classifiers: Collection<KClassifier>,
): MutableSet<RElementType> {
    return classifiers.fold(
        mutableSetOf<RElementType>(),
        { acc, classifier ->
            val realmFieldType = io.realm.internal.classifiers[classifier] ?: error("Unmapped classifier $classifier")
            if (realmFieldType.nullable) {
                acc.add(RElementType(classifier, true))
            }
            if (realmFieldType.nonNullable) {
                acc.add(RElementType(classifier, false))
            }
            acc
        }
    )
}

//
val allElementClassifiers: Set<KClassifier> = classifiers.keys
val allElementTypes = elementTypes(allElementClassifiers)
val allSingularTypes = allElementTypes.map { RType(CollectionType.RLM_COLLECTION_TYPE_NONE, it) }
val allListTypes = allElementTypes.filter { it.realmFieldType.listSupport }.map { RType(CollectionType.RLM_COLLECTION_TYPE_LIST, it) }
// TODO Set
// TODO Dict
val allTypes = allSingularTypes + allListTypes
val allPrimaryKeyTypes = allTypes.filter { it.isPrimaryKeySupported }

// Realm field type
data class RType(
    val collectionType: CollectionType,
    val elementType: RElementType
) {
    val isPrimaryKeySupported: Boolean =
        collectionType == CollectionType.RLM_COLLECTION_TYPE_NONE && elementType.realmFieldType.primaryKeySupport

    fun toKotlinLiteral(): String {
        val elementType = this.elementType
        val element = (elementType.classifier as KClass<*>).simpleName + (if (elementType.nullable) "?" else "")
        return when (collectionType) {
            CollectionType.RLM_COLLECTION_TYPE_NONE -> element
            CollectionType.RLM_COLLECTION_TYPE_LIST -> "List<$element>"
            CollectionType.RLM_COLLECTION_TYPE_SET -> TODO()
            CollectionType.RLM_COLLECTION_TYPE_DICTIONARY -> TODO()
        }
    }

    override fun toString(): String {
        return "RType(collectionType=$collectionType, elementType=$elementType)"
    }
}

fun KType.rType(): RType {
    val elementType = elementType(this)
    return RType(
        collectionType(this),
        RElementType(elementType.classifier!!, elementType.isMarkedNullable)
    )
}

fun KMutableProperty1<*, *>.rType(): RType {
    return this.returnType.rType()
}

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

private fun elementType(type: KType) = when (collectionType(type)) {
    CollectionType.RLM_COLLECTION_TYPE_NONE ->
        type
    CollectionType.RLM_COLLECTION_TYPE_SET,
    CollectionType.RLM_COLLECTION_TYPE_LIST ->
        type.arguments[0].type!!
    CollectionType.RLM_COLLECTION_TYPE_DICTIONARY ->
        type.arguments[1].type!!
}
