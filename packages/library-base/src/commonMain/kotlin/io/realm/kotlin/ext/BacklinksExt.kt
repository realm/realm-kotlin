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
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.internal.interop.ClassKey
import io.realm.kotlin.internal.interop.NativePointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmObjectT
import io.realm.kotlin.internal.realmObjectReference
import io.realm.kotlin.internal.toRealmObject
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BacklinksDelegate
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
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

/**
 * Gets the parent of the embedded object, embedded objects always have an unique parent, that could
 * be [RealmObject] or another [EmbeddedRealmObject].
 *
 * If known, the type parameter can be used to cast it to the parent type. Other approach is to cast
 * it to the generic [TypedRealmObject] and then switch over its possible types:
 *
 * ```
 * val parent: TypedRealmObject = child.parent()
 * when(parent) {
 *  is Parent1 -> TODO()
 *  is Parent2 -> TODO()
 *  is EmbeddedParent1 -> TODO()
 *  else -> TODO()
 * }
 * ```
 *
 * @param T parent type.
 * @return parent of the embedded object.
 */
public fun <T : TypedRealmObject> EmbeddedRealmObject.parent(): T {
    if (!this.isManaged()) {
        throw IllegalStateException("Unmanaged embedded objects don't support parent access.")
    }

    return with(this.realmObjectReference!!) {
        RealmInterop.realm_object_get_parent(
            objectPointer
        ) { classKey: ClassKey, objectPointer: NativePointer<RealmObjectT> ->
            val sourceClassMetadata = owner.schemaMetadata[classKey]!!

            @Suppress("UNCHECKED_CAST")
            RealmObjectReference(
                type = sourceClassMetadata.clazz!! as KClass<T>,
                owner = owner,
                mediator = mediator,
                className = sourceClassMetadata.className,
                objectPointer = objectPointer
            ).toRealmObject()
        }
    }
}
