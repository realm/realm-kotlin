/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.ext

import io.realm.kotlin.internal.BacklinksDelegateImpl
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BacklinksDelegate
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet
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
 * Backlinks for one-to-one relationships can be created on [RealmObject] properties:
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
 * Backlinks for one-to-many relationships can be created on [RealmList], [RealmSet] or
 * [RealmDictionary] properties:
 *
 * ```
 * class Parent : RealmObject {
 *  var childrenList: RealmList<Child> = realmListOf()
 *  var childrenSet: RealmSet<Child> = realmSetOf()
 *  var childrenDictionary: RealmSet<Child?> = realmDictionaryOf() // Nullability of Child? is required by RealmDictionary
 * }
 *
 * class Child : RealmObject {
 *  val parentsFromList: RealmResults<Parent> by backlinks(Parent::childrenList)
 *  val parentsFromSet: RealmResults<Parent> by backlinks(Parent::childrenSet)
 *  val parentsFromDictionary: RealmResults<Parent> by backlinks(Parent::childrenDictionary)
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
public fun <T : TypedRealmObject> RealmObject.backlinks(
    sourceProperty: KProperty1<T, *>,
    sourceClass: KClass<T>
): BacklinksDelegate<T> = BacklinksDelegateImpl(sourceClass)

/**
 * Returns a [BacklinksDelegate] that represents the inverse relationship between two Realm
 * models.
 *
 * Reified convenience wrapper for [RealmObject.backlinks].
 */
public inline fun <reified T : TypedRealmObject> RealmObject.backlinks(
    sourceProperty: KProperty1<T, *>
): BacklinksDelegate<T> = backlinks(sourceProperty, T::class)
