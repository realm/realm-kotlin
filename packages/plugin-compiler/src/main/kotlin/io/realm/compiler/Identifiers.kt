package io.realm.compiler

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal object Names {
    val OBJECT_POINTER = Name.identifier("objectPointer")
    val REALM_POINTER = Name.identifier("realmPointer")
    val OBJECT_TABLE_NAME = Name.identifier("tableName")
    val SET = Name.special("<set-?>")
}

internal object FqNames {
    val REALM_OBJECT_ANNOTATION = FqName("io.realm.runtimeapi.RealmObject")
}
