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

package io.realm.compiler

import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    const val REALM_SYNTHETIC_PROPERTY_PREFIX = "io_realm_kotlin_"

    val REALM_OBJECT_COMPANION_CLASS_NAME_MEMBER: Name = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}className")
    val REALM_OBJECT_COMPANION_FIELDS_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}fields")
    val REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}primaryKey")
    val REALM_OBJECT_COMPANION_SCHEMA_METHOD: Name =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")
    val REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD =
        Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}newInstance")

    val SET = Name.special("<set-?>")

    // names must match `RealmObjectInternal` properties
    val OBJECT_REFERENCE = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}objectReference")

    // C-interop methods
    val REALM_OBJECT_HELPER_GET_VALUE = Name.identifier("getValue")
    val REALM_OBJECT_HELPER_SET_VALUE = Name.identifier("setValue")
    val REALM_OBJECT_HELPER_GET_TIMESTAMP = Name.identifier("getTimestamp")
    val REALM_OBJECT_HELPER_SET_TIMESTAMP = Name.identifier("setTimestamp")
    val REALM_OBJECT_HELPER_GET_OBJECT_ID = Name.identifier("getObjectId")
    val REALM_OBJECT_HELPER_SET_OBJECT_ID = Name.identifier("setObjectId")
    val REALM_OBJECT_HELPER_GET_OBJECT = Name.identifier("getObject")
    val REALM_OBJECT_HELPER_SET_OBJECT = Name.identifier("setObject")
    val REALM_OBJECT_HELPER_GET_LIST = Name.identifier("getList")
    val REALM_OBJECT_HELPER_SET_LIST = Name.identifier("setList")

    // Schema related names
    val CLASS_INFO_CREATE = Name.identifier("create")
    val PROPERTY_INFO_CREATE = Name.identifier("create")
    val PROPERTY_TYPE_OBJECT = Name.identifier("RLM_PROPERTY_TYPE_OBJECT")
    val PROPERTY_COLLECTION_TYPE_NONE = Name.identifier("RLM_COLLECTION_TYPE_NONE")
    val PROPERTY_COLLECTION_TYPE_LIST = Name.identifier("RLM_COLLECTION_TYPE_LIST")
}

internal object FqNames {
    // TODO we can replace with RealmObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val REALM_NATIVE_POINTER = FqName("io.realm.internal.interop.NativePointer")
    val REALM_OBJECT_INTERNAL_INTERFACE = FqName("io.realm.internal.RealmObjectInternal")
    val REALM_MODULE_ANNOTATION = FqName("io.realm.RealmModule")
    val REALM_MODEL_INTERFACE = FqName("io.realm.RealmObject")
    val REALM_MODEL_COMPANION = FqName("io.realm.internal.RealmObjectCompanion")
    val REALM_OBJECT_HELPER = FqName("io.realm.internal.RealmObjectHelper")
    val REALM_CLASS_IMPL = FqName("io.realm.internal.schema.RealmClassImpl")
    val OBJECT_REFERENCE_CLASS = FqName("io.realm.internal.RealmObjectReference")

    // External visible interface of Realm objects
    val KOTLIN_COLLECTIONS_SET = FqName("kotlin.collections.Set")
    val KOTLIN_COLLECTIONS_LIST = FqName("kotlin.collections.List")
    val KOTLIN_COLLECTIONS_LISTOF = FqName("kotlin.collections.listOf")
    val KOTLIN_REFLECT_KPROPERTY1 = FqName("kotlin.reflect.KMutableProperty1")

    // Schema related types
    val CLASS_INFO = FqName("io.realm.internal.interop.ClassInfo")
    val PROPERTY_INFO = FqName("io.realm.internal.interop.PropertyInfo")
    val PROPERTY_TYPE = FqName("io.realm.internal.interop.PropertyType")
    val COLLECTION_TYPE = FqName("io.realm.internal.interop.CollectionType")
    val PRIMARY_KEY_ANNOTATION = FqName("io.realm.annotations.PrimaryKey")
    val INDEX_ANNOTATION = FqName("io.realm.annotations.Index")
    val IGNORE_ANNOTATION = FqName("io.realm.annotations.Ignore")
    val TRANSIENT_ANNOTATION = FqName("kotlin.jvm.Transient")
    val MODEL_OBJECT_ANNOTATION = FqName("io.realm.internal.platform.ModelObject")

    // Realm data types
    val REALM_LIST = FqName("io.realm.RealmList")
    val REALM_INSTANT = FqName("io.realm.RealmInstant")
    val REALM_OBJECT_ID = FqName("io.realm.ObjectId")
}
