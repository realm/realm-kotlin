package io.realm.runtimeapi

interface RealmCompanion {
    // TODO Should be properly types i.e. io.realm.interop.Table, which require it to be in the cinterop module
    fun `$realm$schema`(): String
    // TODO Consider adding additional methods
    // TODO Consider adding type parameter for the class
    // fun create(): T
}
