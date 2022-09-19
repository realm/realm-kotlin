package io.realm.kotlin.internal

import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.RealmLinkingObjects
import io.realm.kotlin.types.RealmObject
import kotlin.reflect.KProperty

internal class RealmLinkingObjectsImpl<T : RealmObject>: RealmLinkingObjects<T> {
    override fun getValue(child: RealmObject, parentProperty: KProperty<*>): RealmResults<T> {
        TODO("Not yet implemented")
    }
}