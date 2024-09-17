/*
 * Copyright 2020 Realm Inc.
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

package io.realm.kotlin.compiler

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    const val REALM_SYNTHETIC_PROPERTY_PREFIX = "io_realm_kotlin_"

    val REALM_OBJECT: Name = Name.identifier("RealmObject")
    val EMBEDDED_REALM_OBJECT: Name = Name.identifier("EmbeddedRealmObject")

    val REALM_OBJECT_COMPANION_CLASS_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}class")
    val REALM_OBJECT_COMPANION_CLASS_NAME_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}className")
    val REALM_OBJECT_COMPANION_FIELDS_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}fields")
    val REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}primaryKey")
    val REALM_OBJECT_COMPANION_CLASS_KIND: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}classKind")
    val REALM_OBJECT_COMPANION_SCHEMA_METHOD: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")
    val REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}newInstance")
    val REALM_OBJECT_TO_STRING_METHOD = Name.identifier("toString")
    val REALM_OBJECT_EQUALS = Name.identifier("equals")
    val REALM_OBJECT_HASH_CODE = Name.identifier("hashCode")

    val SET = Name.special("<set-?>")

    // names must match `RealmObjectInternal` properties
    val OBJECT_REFERENCE = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}objectReference")

    val REALM_ACCESSOR_HELPER_GET_STRING = Name.identifier("getString")
    val REALM_ACCESSOR_HELPER_GET_LONG = Name.identifier("getLong")
    val REALM_ACCESSOR_HELPER_GET_BOOLEAN = Name.identifier("getBoolean")
    val REALM_ACCESSOR_HELPER_GET_FLOAT = Name.identifier("getFloat")
    val REALM_ACCESSOR_HELPER_GET_DOUBLE = Name.identifier("getDouble")
    val REALM_ACCESSOR_HELPER_GET_DECIMAL128 = Name.identifier("getDecimal128")
    val REALM_ACCESSOR_HELPER_GET_INSTANT = Name.identifier("getInstant")
    val REALM_ACCESSOR_HELPER_GET_OBJECT_ID = Name.identifier("getObjectId")
    val REALM_ACCESSOR_HELPER_GET_UUID = Name.identifier("getUUID")
    val REALM_ACCESSOR_HELPER_GET_BYTE_ARRAY = Name.identifier("getByteArray")
    val REALM_ACCESSOR_HELPER_SET_VALUE = Name.identifier("setValue")
    val REALM_ACCESSOR_HELPER_GET_REALM_ANY = Name.identifier("getRealmAny")
    val REALM_OBJECT_HELPER_GET_OBJECT = Name.identifier("getObject")
    val REALM_OBJECT_HELPER_SET_OBJECT = Name.identifier("setObject")
    val REALM_OBJECT_HELPER_SET_EMBEDDED_REALM_OBJECT = Name.identifier("setEmbeddedRealmObject")

    // C-interop methods
    val REALM_OBJECT_HELPER_GET_LIST = Name.identifier("getList")
    val REALM_OBJECT_HELPER_SET_LIST = Name.identifier("setList")
    val REALM_OBJECT_HELPER_GET_SET = Name.identifier("getSet")
    val REALM_OBJECT_HELPER_SET_SET = Name.identifier("setSet")
    val REALM_OBJECT_HELPER_GET_DICTIONARY = Name.identifier("getDictionary")
    val REALM_OBJECT_HELPER_SET_DICTIONARY = Name.identifier("setDictionary")
    val REALM_OBJECT_HELPER_GET_MUTABLE_INT = Name.identifier("getMutableInt")

    // Schema related names
    val CLASS_INFO_CREATE = Name.identifier("create")
    val PROPERTY_TYPE_OBJECT = Name.identifier("RLM_PROPERTY_TYPE_OBJECT")
    val PROPERTY_TYPE_LINKING_OBJECTS = Name.identifier("RLM_PROPERTY_TYPE_LINKING_OBJECTS")
    val PROPERTY_COLLECTION_TYPE_NONE = Name.identifier("RLM_COLLECTION_TYPE_NONE")
    val PROPERTY_COLLECTION_TYPE_LIST = Name.identifier("RLM_COLLECTION_TYPE_LIST")
    val PROPERTY_COLLECTION_TYPE_SET = Name.identifier("RLM_COLLECTION_TYPE_SET")
    val PROPERTY_COLLECTION_TYPE_DICTIONARY = Name.identifier("RLM_COLLECTION_TYPE_DICTIONARY")

    val APP_CREATE = Name.identifier("create")
    val APP_CONFIGURATION_CREATE = Name.identifier("create")
    val APP_CONFIGURATION_BUILDER_BUILD = Name.identifier("build")
}

internal object FqNames {
    val PACKAGE_ANNOTATIONS = FqName("io.realm.kotlin.types.annotations")
    val PACKAGE_KBSON = FqName("org.mongodb.kbson")
    val PACKAGE_KOTLIN_COLLECTIONS = FqName("kotlin.collections")
    val PACKAGE_KOTLIN_REFLECT = FqName("kotlin.reflect")
    val PACKAGE_TYPES: FqName = FqName("io.realm.kotlin.types")
    val PACKAGE_REALM_INTEROP = FqName("io.realm.kotlin.internal.interop")
    val PACKAGE_REALM_INTERNAL = FqName("io.realm.kotlin.internal")
    val PACKAGE_MONGODB = FqName("io.realm.kotlin.mongodb")
}

object ClassIds {

    // TODO we can replace with RealmObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val REALM_NATIVE_POINTER = FqName("io.realm.kotlin.internal.interop.NativePointer")
    val REALM_OBJECT_INTERNAL_INTERFACE = ClassId(FqNames.PACKAGE_REALM_INTERNAL, Name.identifier("RealmObjectInternal"))

    val REALM_MODEL_COMPANION = ClassId(FqNames.PACKAGE_REALM_INTERNAL, Name.identifier("RealmObjectCompanion"))
    val REALM_OBJECT_HELPER = ClassId(FqNames.PACKAGE_REALM_INTERNAL, Name.identifier("RealmObjectHelper"))
    val REALM_CLASS_IMPL = ClassId(FqName("io.realm.kotlin.internal.schema"), Name.identifier("RealmClassImpl"))
    val OBJECT_REFERENCE_CLASS = ClassId(FqNames.PACKAGE_REALM_INTERNAL, Name.identifier("RealmObjectReference"))

    val BASE_REALM_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BaseRealmObject"))
    val REALM_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmObject"))
    val TYPED_REALM_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("TypedRealmObject"))
    val EMBEDDED_OBJECT_INTERFACE = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("EmbeddedRealmObject"))

    val CLASS_APP_CONFIGURATION = ClassId(FqNames.PACKAGE_MONGODB, Name.identifier("AppConfiguration"))

    // External visible interface of Realm objects
    val KOTLIN_COLLECTIONS_SET = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("Set"))
    val KOTLIN_COLLECTIONS_LIST = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("List"))
    val KOTLIN_COLLECTIONS_LISTOF = CallableId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("listOf"))
    val KOTLIN_COLLECTIONS_MAP = ClassId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("Map"))
    val KOTLIN_COLLECTIONS_MAPOF = CallableId(FqNames.PACKAGE_KOTLIN_COLLECTIONS, Name.identifier("mapOf"))
    val KOTLIN_REFLECT_KMUTABLEPROPERTY1 = ClassId(FqNames.PACKAGE_KOTLIN_REFLECT, Name.identifier("KMutableProperty1"))
    val KOTLIN_REFLECT_KPROPERTY1 = ClassId(FqNames.PACKAGE_KOTLIN_REFLECT, Name.identifier("KProperty1"))
    val KOTLIN_PAIR = ClassId(FqName("kotlin"), Name.identifier("Pair"))

    // Schema related types
    val CLASS_INFO = ClassId(FqNames.PACKAGE_REALM_INTEROP, Name.identifier("ClassInfo"))
    val PROPERTY_INFO = ClassId(FqNames.PACKAGE_REALM_INTEROP, Name.identifier("PropertyInfo"))
    val PROPERTY_TYPE = ClassId(FqNames.PACKAGE_REALM_INTEROP, Name.identifier("PropertyType"))
    val COLLECTION_TYPE = ClassId(FqNames.PACKAGE_REALM_INTEROP, Name.identifier("CollectionType"))
    val PRIMARY_KEY_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("PrimaryKey"))
    val INDEX_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("Index"))
    val FULLTEXT_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("FullText"))
    val IGNORE_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("Ignore"))
    val PERSISTED_NAME_ANNOTATION = ClassId(FqNames.PACKAGE_ANNOTATIONS, Name.identifier("PersistedName"))
    val TRANSIENT_ANNOTATION = ClassId(FqName("kotlin.jvm"), Name.identifier("Transient"))
    val MODEL_OBJECT_ANNOTATION = ClassId(FqName("io.realm.kotlin.internal.platform"), Name.identifier("ModelObject"))
    val PROPERTY_INFO_CREATE = CallableId(FqName("io.realm.kotlin.internal.schema"), Name.identifier("createPropertyInfo"))
    val CLASS_KIND_TYPE = ClassId(FqName("io.realm.kotlin.schema"), Name.identifier("RealmClassKind"))

    // Realm data types
    val REALM_LIST = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmList"))
    val REALM_SET = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmSet"))
    val REALM_DICTIONARY = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmDictionary"))
    val REALM_INSTANT = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmInstant"))
    val REALM_BACKLINKS = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("BacklinksDelegate"))
    val REALM_EMBEDDED_BACKLINKS = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("EmbeddedBacklinksDelegate"))
    val KBSON_OBJECT_ID = ClassId(FqNames.PACKAGE_KBSON, Name.identifier("BsonObjectId"))
    val KBSON_DECIMAL128 = ClassId(FqNames.PACKAGE_KBSON, Name.identifier("BsonDecimal128"))
    val REALM_UUID = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmUUID"))
    val REALM_MUTABLE_INTEGER = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("MutableRealmInt"))
    val REALM_ANY = ClassId(FqNames.PACKAGE_TYPES, Name.identifier("RealmAny"))
}
