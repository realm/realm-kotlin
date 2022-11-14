package io.realm.kotlin.ext

import io.realm.kotlin.internal.BacklinksDelegateImpl
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BacklinksDelegate
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Defines a collection of backlinks that represents the inverse relationship between two Realm
 * models. Any direct relationship, one-to-one or one-to-many, can be reversed by backlinks.
 *
 * You cannot directly add or remove items from a backlinks collection. The collection automatically
 * updates itself when relationships are changed.
 *
 * backlinks on a one-to-one relationship:
 *
 * ```
 * class Town {
 *  var county: County? = null
 * }
 *
 * class County {
 *  val towns: RealmResults<Town> by backlinks(Town::county)
 * }
 * ```
 *
 * backlinks on a one-to-many relationship:
 *
 * ```
 * class Parent {
 *  var children: List<Child>? = null
 * }
 *
 * class Child {
 *  val parents: RealmResults<Parent> by backlinks(Parent::children)
 * }
 * ```
 *
 * Querying inverse relationship is like querying any [RealmResults]. This means that an inverse
 * relationship cannot be null but it can be empty (length is 0). It is possible to query fields
 * in the class containing the backlinks field. This is equivalent to link queries.
 *
 * Because Realm lists allow duplicate elements, backlinks might contain duplicate references
 * when the target property is a Realm list and contains multiple references to the same object.
 *
 * @param T type of object that references the model.
 * @param sourceProperty property that references the model.
 * @return delegate for the backlinks collection.
 */
@Suppress("UnusedPrivateMember")
public fun <T : TypedRealmObject> backlinks(
    sourceProperty: KProperty1<T, *>,
    sourceClass: KClass<T>
): BacklinksDelegate<T> =
    BacklinksDelegateImpl(sourceClass)

/**
 * Returns a [BacklinksDelegate] that represents the inverse relationship between two Realm
 * models.
 *
 * Reified convenience wrapper for [backlinks].
 */
public inline fun <reified T : TypedRealmObject> backlinks(sourceProperty: KProperty1<T, *>): BacklinksDelegate<T> =
    backlinks(sourceProperty, T::class)
