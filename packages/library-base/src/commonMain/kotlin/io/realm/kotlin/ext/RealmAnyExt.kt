package io.realm.kotlin.ext

import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny

/**
 * Creates an unmanaged `RealmAny` instance from a [BaseRealmObject] value.
 *
 * Reified convenience wrapper for the [RealmAny.create] for [BaseRealmObject]s.
 */
public inline fun <reified T : BaseRealmObject> RealmAny.asRealmObject(): T =
    asRealmObject(T::class)
