package io.realm.runtimeapi

annotation class RealmObject

interface RealmModelInterface {
    var realmPointer: Long?
    var realmObjectPointer: Long?
    var tableName: String?
    var isManaged: Boolean?
}
