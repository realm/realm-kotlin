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

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    const val REALM_SYNTHETIC_PROPERTY_PREFIX = "io_realm_kotlin_"

    val REALM_OBJECT_COMPANION_CLASS_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}class")
    val REALM_OBJECT_COMPANION_CLASS_NAME_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}className")
    val REALM_OBJECT_COMPANION_FIELDS_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}fields")
    val REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}primaryKey")
    val REALM_OBJECT_COMPANION_IS_EMBEDDED: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}isEmbedded")
    val REALM_OBJECT_COMPANION_SCHEMA_METHOD: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")
    val REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}newInstance")

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
    val PROPERTY_INFO_CREATE = Name.identifier("create")
    val PROPERTY_TYPE_OBJECT = Name.identifier("RLM_PROPERTY_TYPE_OBJECT")
    val PROPERTY_TYPE_LINKING_OBJECTS = Name.identifier("RLM_PROPERTY_TYPE_LINKING_OBJECTS")
    val PROPERTY_COLLECTION_TYPE_NONE = Name.identifier("RLM_COLLECTION_TYPE_NONE")
    val PROPERTY_COLLECTION_TYPE_LIST = Name.identifier("RLM_COLLECTION_TYPE_LIST")
    val PROPERTY_COLLECTION_TYPE_SET = Name.identifier("RLM_COLLECTION_TYPE_SET")
    val PROPERTY_COLLECTION_TYPE_DICTIONARY = Name.identifier("RLM_COLLECTION_TYPE_DICTIONARY")
}

internal object FqNames {
    // TODO we can replace with RealmObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val REALM_NATIVE_POINTER = FqName("io.realm.kotlin.internal.interop.NativePointer")
    val REALM_OBJECT_INTERNAL_INTERFACE = FqName("io.realm.kotlin.internal.RealmObjectInternal")

    val REALM_MODEL_COMPANION = FqName("io.realm.kotlin.internal.RealmObjectCompanion")
    val REALM_OBJECT_HELPER = FqName("io.realm.kotlin.internal.RealmObjectHelper")
    val REALM_CLASS_IMPL = FqName("io.realm.kotlin.internal.schema.RealmClassImpl")
    val OBJECT_REFERENCE_CLASS = FqName("io.realm.kotlin.internal.RealmObjectReference")

    val BASE_REALM_OBJECT_INTERFACE = FqName("io.realm.kotlin.types.BaseRealmObject")
    val REALM_OBJECT_INTERFACE = FqName("io.realm.kotlin.types.RealmObject")
    val EMBEDDED_OBJECT_INTERFACE = FqName("io.realm.kotlin.types.EmbeddedRealmObject")

    // External visible interface of Realm objects
    val KOTLIN_COLLECTIONS_SET = FqName("kotlin.collections.Set")
    val KOTLIN_COLLECTIONS_LIST = FqName("kotlin.collections.List")
    val KOTLIN_COLLECTIONS_LISTOF = FqName("kotlin.collections.listOf")
    val KOTLIN_COLLECTIONS_MAP = FqName("kotlin.collections.Map")
    val KOTLIN_COLLECTIONS_MAPOF = FqName("kotlin.collections.mapOf")
    val KOTLIN_REFLECT_KMUTABLEPROPERTY1 = FqName("kotlin.reflect.KMutableProperty1")
    val KOTLIN_REFLECT_KPROPERTY1 = FqName("kotlin.reflect.KProperty1")
    val KOTLIN_PAIR = FqName("kotlin.Pair")

    // Schema related types
    val CLASS_INFO = FqName("io.realm.kotlin.internal.interop.ClassInfo")
    val PROPERTY_INFO = FqName("io.realm.kotlin.internal.interop.PropertyInfo")
    val PROPERTY_TYPE = FqName("io.realm.kotlin.internal.interop.PropertyType")
    val COLLECTION_TYPE = FqName("io.realm.kotlin.internal.interop.CollectionType")
    val PRIMARY_KEY_ANNOTATION = FqName("io.realm.kotlin.types.annotations.PrimaryKey")
    val INDEX_ANNOTATION = FqName("io.realm.kotlin.types.annotations.Index")
    val IGNORE_ANNOTATION = FqName("io.realm.kotlin.types.annotations.Ignore")
    val PERSISTED_NAME_ANNOTATION = FqName("io.realm.kotlin.types.annotations.PersistedName")
    val TRANSIENT_ANNOTATION = FqName("kotlin.jvm.Transient")
    val MODEL_OBJECT_ANNOTATION = FqName("io.realm.kotlin.internal.platform.ModelObject")

    // Realm data types
    val REALM_LIST = FqName("io.realm.kotlin.types.RealmList")
    val REALM_SET = FqName("io.realm.kotlin.types.RealmSet")
    val REALM_DICTIONARY = FqName("io.realm.kotlin.types.RealmDictionary")
    val REALM_INSTANT = FqName("io.realm.kotlin.types.RealmInstant")
    val REALM_BACKLINKS = FqName("io.realm.kotlin.types.BacklinksDelegate")
    val REALM_EMBEDDED_BACKLINKS = FqName("io.realm.kotlin.types.EmbeddedBacklinksDelegate")
    val REALM_OBJECT_ID = FqName("io.realm.kotlin.types.ObjectId")
    val KBSON_OBJECT_ID = FqName("org.mongodb.kbson.BsonObjectId")
    val KBSON_DECIMAL128 = FqName("org.mongodb.kbson.BsonDecimal128")
    val REALM_UUID = FqName("io.realm.kotlin.types.RealmUUID")
    val REALM_MUTABLE_INTEGER = FqName("io.realm.kotlin.types.MutableRealmInt")
    val REALM_ANY = FqName("io.realm.kotlin.types.RealmAny")
}
