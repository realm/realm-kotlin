package io.realm.kotlin.ext

import io.realm.kotlin.internal.RealmLinkingObjectsDelegateImpl
import io.realm.kotlin.types.RealmLinkingObjectsDelegate
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KProperty1

// TODO document
public fun <T : RealmObject> linkingObjects(targetProperty: KProperty1<T, *>): RealmLinkingObjectsDelegate<T> =
    RealmLinkingObjectsDelegateImpl()
