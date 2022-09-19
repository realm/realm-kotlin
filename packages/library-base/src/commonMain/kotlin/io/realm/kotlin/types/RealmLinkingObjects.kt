package io.realm.kotlin.types

import io.realm.kotlin.internal.RealmLinkingObjectsImpl
import io.realm.kotlin.query.RealmResults
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1

// Ideally we like to express something like this
// public fun <T : RealmObject> linkingObjects(property: KProperty1<T, RealmObject?>): RealmLinkingObjects<T> = TODO()
// public fun <T : RealmObject> linkingObjects(property: KProperty1<T, RealmList<out RealmObject>>): RealmLinkingObjects<T> = TODO()
// public fun <T : RealmObject> linkingObjects(property: KProperty1<T, RealmSet<out RealmObject>>): RealmLinkingObjects<T> = TODO()

public fun <T : RealmObject> linkingObjects(property: KProperty1<T, *>): RealmLinkingObjects<T> = RealmLinkingObjectsImpl()

public interface RealmLinkingObjects<T : RealmObject> {
    public operator fun getValue(thisRef: RealmObject, property: KProperty<*>): RealmResults<T>
}
