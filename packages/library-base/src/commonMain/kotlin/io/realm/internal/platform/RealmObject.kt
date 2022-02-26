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

import io.realm.RealmObject
import io.realm.internal.RealmObjectCompanion
import kotlin.reflect.KClass

/**
 * Returns the [RealmObjectCompanion] associated with a given [KClass] or null if it didn't have an
 * associated [RealmObjectCompanion], in which case the `clazz` wasn't a user defined class
 * implementing [RealmObject] augmented by our compiler plugin.
 */
internal expect fun <T : Any> realmObjectCompanionOrNull(clazz: KClass<T>): RealmObjectCompanion?

/**
 * Returns the [RealmObjectCompanion] associated with a given [RealmObject]'s [KClass].
 */
internal expect fun <T : RealmObject> realmObjectCompanionOrThrow(clazz: KClass<T>): RealmObjectCompanion
