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
import io.realm.kotlin.types.EmbeddedBacklinksDelegate
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

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
 * @param parentClass parent KClass.
 * @return parent of the embedded object.
 */
public fun <T : TypedRealmObject> EmbeddedRealmObject.parent(parentClass: KClass<T>): T {
    if (!this.isManaged()) {
        throw IllegalStateException("Unmanaged embedded objects don't support parent access.")
    }

    return with(this.realmObjectReference!!) {
        RealmInterop.realm_object_get_parent(
            objectPointer
        ) { classKey: ClassKey, objectPointer: NativePointer<RealmObjectT> ->
            val sourceClassMetadata = owner.schemaMetadata[classKey] ?: throw IllegalArgumentException("Parent class not defined in the Realm schema.")

            @Suppress("UNCHECKED_CAST")
            val sourceClass = (sourceClassMetadata.clazz!! as KClass<T>)

            RealmObjectReference(
                type = sourceClass,
                owner = owner,
                mediator = mediator,
                className = sourceClassMetadata.className,
                objectPointer = objectPointer
            ).toRealmObject()
                .also { realmObject: T ->
                    if (!parentClass.isInstance(realmObject)) {
                        throw ClassCastException("${sourceClass.qualifiedName} cannot be cast to ${parentClass.qualifiedName}")
                    }
                }
        }
    }
}

/**
 * Returns a [TypedRealmObject] that represents the parent that hosts the embedded object.
 *
 * Reified convenience wrapper for [EmbeddedRealmObject.parent].
 */
public inline fun <reified T : TypedRealmObject> EmbeddedRealmObject.parent(): T = parent(T::class)


/**
 * Defines a backlink that represents a one-to-one inverse relationship between an [EmbeddedRealmObject] and a
 * [TypedRealmObject].
 *
 * You cannot directly modify the backlink itself, it must be done on the pointing object. Usage example:
 *
 * ```
 * class Town : EmbeddedRealmObject {
 *  val county: County by backlinks(County::towns)
 * }
 *
 * class County : RealmObject {
 *  val towns: RealmList<Town> = realmListOf()
 * }
 * ```
 *
 * Because an [EmbeddedRealmObject] class might be referenced by different [TypedRealmObject] than
 * the defined by the backlinks. In such cases an exception would be thrown stating that the pointing
 * object could not might not be able to resolve into a [T] value.
 *
 * @param T type of object that references the model.
 * @param sourceProperty property that references the model.
 * @return delegate for the backlinks collection.
 * @throws IllegalStateException if the backlink is not of type [T]
 */
@Suppress("UnusedPrivateMember") // Used by the compiler plugin
public fun <T : TypedRealmObject> EmbeddedRealmObject.backlinks(
    sourceProperty: KProperty1<T, *>,
    sourceClass: KClass<T>
): EmbeddedBacklinksDelegate<T> = BacklinksDelegateImpl(sourceClass)

/**
 * Returns a [BacklinksDelegate] that represents the inverse relationship between two an
 * [EmbeddedRealmObject] and a [TypedRealmObject].
 *
 * Reified convenience wrapper for [EmbeddedRealmObject.backlinks].
 */
public inline fun <reified T : TypedRealmObject> EmbeddedRealmObject.backlinks(
    sourceProperty: KProperty1<T, *>
): EmbeddedBacklinksDelegate<T> = this.backlinks(sourceProperty, T::class)
