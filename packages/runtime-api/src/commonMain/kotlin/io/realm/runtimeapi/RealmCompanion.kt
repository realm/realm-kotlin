package io.realm.runtimeapi

interface RealmCompanion {
    // FIXME MEDIATOR Should be properly types i.e. io.realm.interop.Table, which require it to be in the cinterop module
    fun `$realm$schema`(): String
    // FIXME MEDIATOR/API-INTERNAL Should be properly typed i.e. io.realm.interop.Table, which
    //  require it to be in the cinterop module
    // TODO MEDIATOR/API-INTERNAL Consider adding additional methods
    // TODO MEDIATOR/API-INTERNAL Consider adding type parameter for the class
    // fun create(): T
}
