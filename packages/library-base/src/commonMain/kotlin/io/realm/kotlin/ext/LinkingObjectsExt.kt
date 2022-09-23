package io.realm.kotlin.ext

import io.realm.kotlin.internal.RealmLinkingObjectsImpl
import io.realm.kotlin.types.RealmLinkingObjects
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

// TODO document
public fun <T : RealmObject> linkingObjects(targetProperty: KProperty1<T, *>, clazz: KClass<T>): RealmLinkingObjects<T> =
    RealmLinkingObjectsImpl(targetProperty, clazz)

// TODO document
public inline fun <reified T : RealmObject> linkingObjects(targetProperty: KProperty1<T, *>): RealmLinkingObjects<T> =
    linkingObjects(targetProperty, T::class)
