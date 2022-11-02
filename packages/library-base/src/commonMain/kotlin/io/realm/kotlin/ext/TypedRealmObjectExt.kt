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

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.LinkingObjectsDelegateImpl
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.LinkingObjectsDelegate
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * Makes an unmanaged in-memory copy of an already persisted [io.realm.kotlin.types.RealmObject].
 * This is a deep copy that will copy all referenced objects.
 *
 * @param obj managed object to copy from the Realm.
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @param closeAfterCopy Whether or not to close a Realm object after it has been copied. If
 * an object is closed, `RealmObject.isValid()` will return `false` and further access to it
 * will throw an [IllegalStateException]. This can be beneficial as managed RealmObjects contain
 * a reference to a chunck of native memory. This memory is normally freed when the object is
 * garbage collected by Kotlin. However, manually closing the object allow Realm to free that
 * memory immediately, allowing for better native memory management and control over the size
 * of the Realm file.
 * @returns an in-memory copy of the input object.
 * @throws IllegalArgumentException if depth < 0 or the object  is not a valid object to copy.
 */
public inline fun <reified T : RealmObject> T.copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth, closeAfterCopy)
    } ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}

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
public fun <T : TypedRealmObject> linkingObjects(
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
public inline fun <reified T : TypedRealmObject> linkingObjects(sourceProperty: KProperty1<T, *>): LinkingObjectsDelegate<T> =
    linkingObjects(sourceProperty, T::class)
