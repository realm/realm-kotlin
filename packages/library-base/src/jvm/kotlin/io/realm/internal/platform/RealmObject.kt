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

import io.realm.BaseRealmObject
import io.realm.internal.RealmObjectCompanion
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObjectInstance

// TODO OPTIMIZE Can we eliminate the reflective approach? Maybe by embedding the information
//  through the compiler plugin or something similar to the Native findAssociatedObject
internal actual fun <T : Any> realmObjectCompanionOrNull(clazz: KClass<T>): RealmObjectCompanion? =
    if (clazz.companionObjectInstance is RealmObjectCompanion) {
        clazz.companionObjectInstance as RealmObjectCompanion
    } else null

internal actual fun <T : BaseRealmObject> realmObjectCompanionOrThrow(clazz: KClass<T>): RealmObjectCompanion =
    realmObjectCompanionOrNull(clazz)
        ?: error("Couldn't find companion object of class '${clazz.simpleName}'.\nA common cause for this is when the `io.realm.kotlin` is not applied to the Gradle module that contains the '${clazz.simpleName}' class.")
