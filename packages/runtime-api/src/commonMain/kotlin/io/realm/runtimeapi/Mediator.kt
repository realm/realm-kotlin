package io.realm.runtimeapi

import kotlin.reflect.KClass

interface Mediator { // avoid reflection, implemented and defined by compiler plugin for each `@RealmModule`
    fun newInstance(clazz: KClass<*>): Any
    // The compiler should build a list of io.realm.interop.Table
    fun schema(): List<Any>
}
