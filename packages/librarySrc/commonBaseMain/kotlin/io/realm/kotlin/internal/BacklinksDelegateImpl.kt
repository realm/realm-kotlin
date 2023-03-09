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

package io.realm.kotlin.internal

import io.realm.kotlin.ext.isManaged
import io.realm.kotlin.internal.schema.ClassMetadata
import io.realm.kotlin.internal.schema.PropertyMetadata
import io.realm.kotlin.query.RealmResults
import io.realm.kotlin.types.BacklinksDelegate
import io.realm.kotlin.types.EmbeddedBacklinksDelegate
import io.realm.kotlin.types.EmbeddedRealmObject
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal class BacklinksDelegateImpl<T : TypedRealmObject>(
    private val sourceClass: KClass<T>
) : EmbeddedBacklinksDelegate<T>, BacklinksDelegate<T> {
    private fun createBacklinks(
        reference: TypedRealmObject,
        targetProperty: KProperty<*>
    ): RealmResults<T> {
        if (!reference.isManaged()) {
            throw IllegalStateException("Unmanaged objects don't support backlinks.")
        }
        val objectReference = reference.realmObjectReference!!

        // Target property must be part of the reference object.
        val targetPropertyMetadata: PropertyMetadata = objectReference.metadata[targetProperty]
            ?: throw IllegalArgumentException("Target property '${targetProperty.name}' not defined in '${reference::class.simpleName}'.")

        // Target property must be a backlink.
        targetPropertyMetadata.linkOriginPropertyName.ifEmpty {
            throw IllegalArgumentException("Target property '${targetProperty.name}' is not a backlink property.")
        }

        val sourceClassMetadata: ClassMetadata = objectReference.owner
            .schemaMetadata
            .getOrThrow(targetPropertyMetadata.linkTarget)

        // Delegate type parameter T and source class must match.
        if (sourceClass != sourceClassMetadata.clazz) {
            throw IllegalArgumentException("Target property type '${sourceClassMetadata.clazz!!.simpleName}' does not match backlink type '${sourceClass.simpleName}'.")
        }

        val sourcePropertyKey = sourceClassMetadata[targetPropertyMetadata.linkOriginPropertyName]!!

        val linkingObjects: RealmResultsImpl<T> = RealmObjectHelper.getBacklinks(
            obj = objectReference,
            sourcePropertyKey = sourcePropertyKey.key,
            sourceClassKey = sourceClassMetadata.classKey,
            sourceClass = sourceClass
        )

        return ObjectBoundRealmResults(objectReference, linkingObjects)
    }

    override fun getValue(
        reference: EmbeddedRealmObject,
        targetProperty: KProperty<*>
    ): T = createBacklinks(reference, targetProperty).firstOrNull()
        ?: throw IllegalStateException("Backlink '${targetProperty.name}' is not an instance of target property type '${sourceClass!!.simpleName}'.")

    override fun getValue(
        reference: RealmObject,
        targetProperty: KProperty<*>
    ): RealmResults<T> = createBacklinks(reference, targetProperty)
}
