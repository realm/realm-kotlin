package io.realm.kotlin.ext

import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmObject

/**
 * Creates an unmanaged `RealmAny` instance from a [RealmObject] value.
 *
 * Reified convenience wrapper for the [RealmAny.create] for [RealmObject]s.
 */
public inline fun <reified T : RealmObject> RealmAny.asRealmObject(): T =
    asRealmObject(T::class)
