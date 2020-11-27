package io.realm.runtimeapi

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class RealmModule(vararg val models: KClass<*>)
