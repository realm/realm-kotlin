package io.realm

import kotlin.reflect.KClass

internal interface ProxyInstantiator<T : RealmModel> {
    fun getInstance(type: KClass<T>): T
}
