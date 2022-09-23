package io.realm.kotlin.ext

import io.realm.kotlin.internal.RealmLinkingObjectsImpl
import io.realm.kotlin.types.RealmLinkingObjects
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KProperty1

// TODO document
public fun <T : RealmObject> linkingObjects(targetProperty: KProperty1<T, *>): RealmLinkingObjects<T> =
    RealmLinkingObjectsImpl()
