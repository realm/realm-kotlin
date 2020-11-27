package io.realm.runtimeapi

import kotlin.reflect.KClass

// FIXME https://github.com/realm/realm-kotlin/issues/90 support default schema creation.
interface Mediator { // avoid reflection, implemented and defined by compiler plugin for each `@RealmModule`
    fun newInstance(clazz: KClass<*>): Any
    // The compiler should build a list of io.realm.interop.Table
    fun schema(): List<Any>
}
