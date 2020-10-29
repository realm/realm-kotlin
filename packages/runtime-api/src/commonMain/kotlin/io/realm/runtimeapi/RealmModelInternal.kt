package io.realm.runtimeapi

annotation class RealmObject

/**
 * This interface is added by the compiler plugin to all [RealmObject] annotated classes, it contains
 * internal properties of the model.
 *
 * This interface is not meant to be used externally (consider using [RealmModel] instead)
 */
interface RealmModelInternal : RealmModel {
    var realmPointer: NativePointer?
    var realmObjectPointer: NativePointer?
    var tableName: String?
    var isManaged: Boolean?
}
