package io.realm.runtimeapi

annotation class RealmObject

/**
 * This interface is added by the compiler plugin to all [RealmObject] annotated classes, it contains
 * internal properties of the model.
 *
 * This interface is not meant to be used externally (consider using [RealmModel] instead)
 */
@Suppress("VariableNaming")
interface RealmModelInternal : RealmModel {
    var `$realm$Pointer`: NativePointer?
    var `$realm$ObjectPointer`: NativePointer?
    var `$realm$TableName`: String?
    var `$realm$IsManaged`: Boolean
}
