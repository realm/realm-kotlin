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

    val REALM_OBJECT_COMPANION_FIELDS_MEMBER: Name = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}fields")
    val REALM_OBJECT_COMPANION_PRIMARY_KEY_MEMBER: Name = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}primaryKey")
    val REALM_OBJECT_COMPANION_SCHEMA_METHOD: Name = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")
    val REALM_OBJECT_COMPANION_NEW_INSTANCE_METHOD = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}newInstance")

    val SET = Name.special("<set-?>")
    // names must match `RealmObjectInterop` properties
    val OBJECT_POINTER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}ObjectPointer")
    // names must match `RealmObjectInternal` properties
    val REALM_OWNER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}Owner")
    val OBJECT_CLASS_NAME = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}TableName")
    val OBJECT_IS_MANAGED = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}IsManaged")
    val MEDIATOR = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}Mediator")

    // C-interop methods
    val REALM_OBJECT_HELPER_GET_VALUE = Name.identifier("getValue")
    val REALM_OBJECT_HELPER_SET_VALUE = Name.identifier("setValue")
    val REALM_OBJECT_HELPER_GET_TIMESTAMP = Name.identifier("getTimestamp")
    val REALM_OBJECT_HELPER_SET_TIMESTAMP = Name.identifier("setTimestamp")
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

    // Function names
    val REALM_CONFIGURATION_BUILDER_BUILD = Name.identifier("build")
    val REALM_CONFIGURATION_WITH = Name.identifier("with")
    val REALM_OBJECT_INTERNAL_IS_FROZEN = Name.identifier("isFrozen")
    val REALM_OBJECT_INTERNAL_REALM_STATE = Name.identifier("realmState")
    val REALM_OBJECT_INTERNAL_VERSION = Name.identifier("version")
}

internal object FqNames {
    // TODO we can replace with RealmObject::class.java.canonicalName if we make the runtime_api available as a compile time only dependency for the compiler-plugin
    val REALM_NATIVE_POINTER = FqName("io.realm.internal.interop.NativePointer")
    val REALM_OBJECT_INTERNAL_INTERFACE = FqName("io.realm.internal.RealmObjectInternal")
    val REALM_MODULE_ANNOTATION = FqName("io.realm.RealmModule")
    val REALM_MODEL_INTERFACE = FqName("io.realm.RealmObject")
    val REALM_MODEL_COMPANION = FqName("io.realm.internal.RealmObjectCompanion")
    val REALM_OBJECT_HELPER = FqName("io.realm.internal.RealmObjectHelper")
    val REALM_REFERENCE = FqName("io.realm.internal.RealmReference")
    val REALM_MEDIATOR_INTERFACE = FqName("io.realm.internal.Mediator")
    val REALM_CLASS_IMPL = FqName("io.realm.internal.schema.RealmClassImpl")
    val REALM_CONFIGURATION = FqName("io.realm.RealmConfiguration")
    val SYNC_CONFIGURATION = FqName("io.realm.mongodb.SyncConfiguration")
    val REALM_CONFIGURATION_BUILDER = FqName("io.realm.RealmConfiguration.Builder")
    val SYNC_CONFIGURATION_BUILDER = FqName("io.realm.mongodb.SyncConfiguration.Builder")
    // External visible interface of Realm objects
    val KOTLIN_COLLECTIONS_SET = FqName("kotlin.collections.Set")
    val KOTLIN_COLLECTION_LIST = FqName("kotlin.collections.List")
    val KOTLIN_PAIR = FqName("kotlin.Pair")
    val KOTLIN_COLLECTIONS_MAP = FqName("kotlin.collections.Map")
    val KOTLIN_COLLECTIONS_LIST = FqName("kotlin.collections.List")
    val KOTLIN_COLLECTIONS_LISTOF = FqName("kotlin.collections.listOf")
    val KOTLIN_COLLECTIONS_MAPOF = FqName("kotlin.collections.mapOf")
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
}
