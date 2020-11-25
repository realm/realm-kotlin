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
    val SCHEMA_METHOD = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}schema")

    val SET = Name.special("<set-?>")
    // names must match `RealmModelInternal` properties
    val REALM_POINTER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}Pointer")
    val OBJECT_POINTER = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}ObjectPointer")
    val OBJECT_TABLE_NAME = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}TableName")
    val OBJECT_IS_MANAGED = Name.identifier("${REALM_SYNTHETIC_PROPERTY_PREFIX}IsManaged")

    // C-interop methods
    val C_INTEROP_OBJECT_GET_STRING = Name.identifier("objectGetString")
    val C_INTEROP_OBJECT_SET_STRING = Name.identifier("objectSetString")
    val C_INTEROP_OBJECT_GET_INT64 = Name.identifier("objectGetInt64")
    val C_INTEROP_OBJECT_SET_INT64 = Name.identifier("objectSetInt64")
    val C_INTEROP_OBJECT_GET_BOOLEAN = Name.identifier("objectGetBoolean")
    val C_INTEROP_OBJECT_SET_BOOLEAN = Name.identifier("objectSetBoolean")
}

internal object FqNames {
    val REALM_NATIVE_POINTER = FqName("io.realm.runtimeapi.NativePointer")
    val REALM_OBJECT_ANNOTATION = FqName("io.realm.runtimeapi.RealmObject")
    val REALM_MODEL_INTERFACE = FqName("io.realm.runtimeapi.RealmModelInternal")
    val REALM_MODEL_COMPANION = FqName("io.realm.runtimeapi.RealmCompanion")
    val NATIVE_WRAPPER = FqName("io.realm.interop.RealmInterop")
    val NATIVE_POINTER = FqName("io.realm.runtimeapi.NativePointer")
    // External visible interface of Realm objects
    val REALM_MODEL_INTERFACE_MARKER = FqName("io.realm.runtimeapi.RealmModel")
}
