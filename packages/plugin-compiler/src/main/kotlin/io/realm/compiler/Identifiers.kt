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
    const val REALM_SYNTHETIC_PROPERTY_PREFIX = "\$realm\$"

    val DEFAULT_COMPANION = Name.identifier("Companion")
    val REALM_OBJECT_COMPANION_SCHEMA_METHOD: Name = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")
    val REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}newInstance")

    val SET = Name.special("<set-?>")
    // names must match `RealmModelInternal` properties
    val REALM_POINTER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}Pointer")
    val OBJECT_POINTER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}ObjectPointer")
    val OBJECT_TABLE_NAME = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}TableName")
    val OBJECT_IS_MANAGED = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}IsManaged")
    val REALM_OBJECT_SCHEMA = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}Schema")

    // RealmMediator methods
    val REALM_MEDIATOR_MAPPING_PROPERTY = Name.identifier("companionMapping")
    val REALM_MEDIATOR_NEW_INSTANCE_METHOD = Name.identifier("newInstance")
    val REALM_MEDIATOR_SCHEMA_METHOD = Name.identifier("schema")

    // C-interop methods
    val REALM_OBJECT_HELPER_GET_VALUE = Name.identifier("getValue")
    val REALM_OBJECT_HELPER_SET_VALUE = Name.identifier("setValue")
    val REALM_OBJECT_HELPER_GET_OBJECT = Name.identifier("getObject")

    // Schema related names
    val CLASS_FLAG_NORMAL = Name.identifier("RLM_CLASS_NORMAL")
    val PROPERTY_FLAG_NULLABLE = Name.identifier("RLM_PROPERTY_NULLABLE")
    val PROPERTY_FLAG_NORMAL = Name.identifier("RLM_PROPERTY_NORMAL")
    val PROPERTY_TYPE_OBJECT = Name.identifier("RLM_PROPERTY_TYPE_OBJECT")
    val PROPERTY_COLLECTION_TYPE_NONE = Name.identifier("RLM_COLLECTION_TYPE_NONE")
}

internal object FqNames {
    // TODO we can replace with RealmObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val REALM_NATIVE_POINTER = FqName("io.realm.runtimeapi.NativePointer")
    val REALM_MODULE_ANNOTATION = FqName("io.realm.runtimeapi.RealmModule")
    val REALM_MODEL_INTERNAL_INTERFACE = FqName("io.realm.runtimeapi.RealmModelInternal")
    val REALM_MODEL_INTERFACE = FqName("io.realm.runtimeapi.RealmObject")
    val REALM_MODEL_COMPANION = FqName("io.realm.internal.RealmObjectCompanion")
    val REALM_OBJECT_HELPER = FqName("io.realm.internal.RealmObjectHelper")
    // External visible interface of Realm objects
    val REALM_MEDIATOR_INTERFACE = FqName("io.realm.internal.Mediator")
    val KOTLIN_COLLECTIONS_SET = FqName("kotlin.collections.Set")
    val KOTLIN_COLLECTION_LIST = FqName("kotlin.collections.List")
    val KOTLIN_COLLECTIONS_HASHMAP = FqName("kotlin.collections.HashMap")
    val JAVA_UTIL_HASHMAP = FqName("java.util.HashMap")
    val KOTLIN_COLLECTIONS_ABSTRACT_COLLECTION = FqName("kotlin.collections.AbstractCollection")
    val JAVA_UTIL_ABSTRACT_COLLECTION = FqName("java.util.AbstractCollection")
    val KOTLIN_COLLECTIONS_LIST = FqName("kotlin.collections.List")
    val KOTLIN_COLLECTIONS_ARRAY_LIST = FqName("kotlin.collections.ArrayList")
    val JAVA_UTIL_ARRAY_LIST = FqName("java.util.ArrayList")
    val KOTLIN_COLLECTIONS_ITERATOR = FqName("kotlin.collections.Iterator")
    val JAVA_UTIL_ITERATOR = FqName("java.util.Iterator")
    val KOTLIN_COLLECTIONS_MUTABLE_COLLECTION = FqName("kotlin.collections.MutableCollection")
    val JAVA_UTIL_COLLECTION = FqName("java.util.Collection")
    // Schema related types
    val TABLE = FqName("io.realm.interop.Table")
    val CLASS_FLAG = FqName("io.realm.interop.ClassFlag")
    val PROPERTY = FqName("io.realm.interop.Property")
    val PROPERTY_TYPE = FqName("io.realm.interop.PropertyType")
    val COLLECTION_TYPE = FqName("io.realm.interop.CollectionType")
    val PROPERTY_FLAG = FqName("io.realm.interop.PropertyFlag")
}
