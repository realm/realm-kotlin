package io.realm.runtimeapi

interface RealmCompanion {
    // TODO Should be properly types i.e. io.realm.interop.Table, which require it to be in the cinterop module
    fun `$realm$schema`(): String // TODO change to use cinterop Table class instead or a marker interface that Table will be implementing
    fun `$realm$newInstance`(): Any
    // TODO Consider adding additional methods
    // TODO Consider adding type parameter for the class
}
