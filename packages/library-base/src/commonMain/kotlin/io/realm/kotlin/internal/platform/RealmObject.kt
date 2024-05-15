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

package io.realm.kotlin.internal.platform

import io.realm.kotlin.internal.RealmObjectCompanion
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KClass

/**
 * Returns the [RealmObjectCompanion] associated with a given [KClass] or null if it didn't have an
 * associated [RealmObjectCompanion], in which case the `clazz` wasn't a user defined class
 * implementing [BaseRealmObject] augmented by our compiler plugin.
 */
@PublishedApi
internal expect fun <T : Any> realmObjectCompanionOrNull(clazz: KClass<T>): RealmObjectCompanion?

/**
 * Returns the [RealmObjectCompanion] associated with a given [BaseRealmObject]'s [KClass].
 */

@PublishedApi
internal expect fun <T : BaseRealmObject> realmObjectCompanionOrThrow(clazz: KClass<T>): RealmObjectCompanion
