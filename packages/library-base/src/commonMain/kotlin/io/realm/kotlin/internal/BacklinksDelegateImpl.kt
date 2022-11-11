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
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.TypedRealmObject
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

internal class BacklinksDelegateImpl<T : TypedRealmObject>(private val sourceClass: KClass<T>) : BacklinksDelegate<T> {
    override fun getValue(
        reference: RealmObject,
        targetProperty: KProperty<*>
    ): RealmResults<T> {
        if (!reference.isManaged()) {
            throw IllegalStateException("Unmanaged objects don't support backlinks.")
        }
        val objectReference = reference.realmObjectReference!!
        val targetPropertyMetadata: PropertyMetadata =
            objectReference.metadata[targetProperty]!!

        val sourceClassMetadata: ClassMetadata = objectReference.owner
            .schemaMetadata
            .getOrThrow(targetPropertyMetadata.linkTarget)

        val sourcePropertyKey = sourceClassMetadata[targetPropertyMetadata.linkOriginPropertyName]!!.key

        val linkingObjects: RealmResultsImpl<T> = RealmObjectHelper.getBacklinks(
            obj = objectReference,
            sourcePropertyKey = sourcePropertyKey,
            sourceClassKey = sourceClassMetadata.classKey,
            sourceClass = sourceClass
        )

        return ObjectBoundRealmResults(objectReference, linkingObjects)
    }
}
