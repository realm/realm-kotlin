package io.realm

import kotlin.reflect.KClass

interface ProxyInstantiator<T : RealmModel> {
    fun getInstance(type: KClass<T>): T
}
