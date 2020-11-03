package io.realm.runtimeapi

interface RealmCompanion {
    fun `$realm$schema`(): String // TODO change to use cinterop Table class instead or a marker interface that Table will be implementing
    fun `$realm$newInstance`(): Any
}
