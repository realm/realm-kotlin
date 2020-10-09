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
}

internal object FqNames {
    val REALM_OBJECT_ANNOTATION = FqName("io.realm.runtimeapi.RealmObject")
    val REALM_MODEL_INTERFACE = FqName("io.realm.runtimeapi.RealmModelInternal")
    val REALM_MODEL_COMPANION = FqName("io.realm.runtimeapi.RealmCompanion")
    val NATIVE_WRAPPER = FqName("io.realm.runtimeapi.NativeWrapper")
    val NATIVE_POINTER = FqName("io.realm.runtimeapi.NativePointer")
    // External visible interface of Realm objects
    val REALM_MODEL_INTERFACE_MARKER = FqName("io.realm.runtimeapi.RealmModel")
}
