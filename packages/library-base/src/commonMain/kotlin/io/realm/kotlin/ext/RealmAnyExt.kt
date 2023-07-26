package io.realm.kotlin.ext

import io.realm.kotlin.dynamic.DynamicRealmObject
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import io.realm.kotlin.types.RealmInstant
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmUUID
import org.mongodb.kbson.Decimal128
import org.mongodb.kbson.ObjectId

/**
 * Creates an unmanaged `RealmAny` instance from a [BaseRealmObject] value.
 *
 * Reified convenience wrapper for the [RealmAny.create] for [RealmObject]s.
 */
public inline fun <reified T : BaseRealmObject> RealmAny.asRealmObject(): T =
    asRealmObject(T::class)

// FIXME Doc if this is public
public fun realmAnyOf(value: Any?): RealmAny? {
    return when(value) {
        (value == null) -> null
        is Boolean -> RealmAny.create(value)
        is Byte -> RealmAny.create(value)
        is Char -> RealmAny.create(value)
        is Short -> RealmAny.create(value)
        is Int -> RealmAny.create(value)
        is Long -> RealmAny.create(value)
        is Float -> RealmAny.create(value)
        is Double -> RealmAny.create(value)
        is String -> RealmAny.create(value)
        is Decimal128 -> RealmAny.create(value)
        is ObjectId -> RealmAny.create(value)
        is ByteArray -> RealmAny.create(value)
        is RealmInstant -> RealmAny.create(value)
        is RealmUUID -> RealmAny.create(value)
        is RealmObject -> RealmAny.create(value)
        is DynamicRealmObject -> RealmAny.create(value)
        is Set<*> -> RealmAny.create(value.map { realmAnyOf(it) }.toRealmSet())
        is List<*> -> RealmAny.create(value.map { realmAnyOf(it) }.toRealmList())
        is Map<*, *> -> RealmAny.create(value.map { (key, value) -> key as String to realmAnyOf(value) }.toRealmDictionary())
        is RealmAny -> value
        else -> throw IllegalArgumentException("Cannot create RealmAny from '$value'")
    }
}

// FIXME Doc
public fun realmAnySetOf(vararg values: Any?): RealmAny =
    RealmAny.create(values.map { realmAnyOf(it) }.toRealmSet())

// FIXME Doc
public fun realmAnyListOf(vararg values: Any?): RealmAny =
    RealmAny.create(values.map { realmAnyOf(it) }.toRealmList())

// FIXME Doc
public fun realmAnyDictionaryOf(vararg values: Pair<String,Any?>): RealmAny =
    RealmAny.create(values.map { (key, value) -> key to realmAnyOf(value) }
        .toRealmDictionary())
