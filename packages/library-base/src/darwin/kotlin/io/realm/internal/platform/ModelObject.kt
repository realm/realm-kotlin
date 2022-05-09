/*
 * Copyright 2021 Realm Inc.
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

package io.realm.internal.platform

import io.realm.internal.RealmObjectCompanion
import kotlin.reflect.AssociatedObjectKey
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass

/**
 * Native-only internal annotation that is used by the compiler plugin to mark the user model
 * classes (implementing [RealmObject]) with its associated companion object (implementing
 * [RealmObjectCompanion]).
 *
 * This allows looking up the companion object at runtime with
 * [findAssociatedObject][https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect/find-associated-object.html].
 */
@OptIn(ExperimentalAssociatedObjects::class)
@AssociatedObjectKey
@Retention(AnnotationRetention.BINARY)
@PublishedApi
internal annotation class ModelObject(public val companionClass: KClass<out RealmObjectCompanion>)
