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

import io.realm.kotlin.internal.LinkingObjectsDelegateImpl
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.LinkingObjectsDelegate
import io.realm.kotlin.types.StaticRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Defines a collection of linking objects that represents the inverse relationship between two Realm
 * models. Any direct relationship, one-to-one or one-to-many, can be reversed by linking objects.
 *
 * You cannot directly add or remove items from a linking objects collection. The collection automatically
 * updates itself when relationships are changed.
 *
 * Linking objects on a one-to-one relationship:
 *
 * ```
 * class Town {
 *  var county: County? = null
 * }
 *
 * class County {
 *  val towns: RealmResults<Town> by linkingObjects(Town::county)
 * }
 * ```
 *
 * Linking objects on a one-to-many relationship:
 *
 * ```
 * class Parent {
 *  var children: List<Child>? = null
 * }
 *
 * class Child {
 *  val parents: RealmResults<Parent> by linkingObjects(Parent::children)
 * }
 * ```
 *
 * Querying inverse relationship is like querying any [RealmResults]. This means that an inverse
 * relationship cannot be null but it can be empty (length is 0). It is possible to query fields
 * in the class containing the linkingObjects field. This is equivalent to link queries.
 *
 * Because Realm lists allow duplicate elements, linking objects might contain duplicate references
 * when the target property is a Realm list and contains multiple references to the same object.
 *
 * @param T type of object that references the model.
 * @param sourceProperty property that references the model.
 * @return delegate for the linking objects collection.
 */
public fun <T : StaticRealmObject> linkingObjects(
    sourceProperty: KProperty1<T, *>,
    sourceClass: KClass<T>
): LinkingObjectsDelegate<T> =
    LinkingObjectsDelegateImpl(sourceClass)

/**
 * Returns a [LinkingObjectsDelegate] that represents the inverse relationship between two Realm
 * models.
 *
 * Reified convenience wrapper for [linkingObjects].
 */
public inline fun <reified T : StaticRealmObject> linkingObjects(sourceProperty: KProperty1<T, *>): LinkingObjectsDelegate<T> =
    linkingObjects(sourceProperty, T::class)
