/*
 * Copyright 2024 Realm Inc.
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

package io.realm.kotlin.dynamic.getgeneric

import io.realm.kotlin.annotations.DynamicAPI
import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.runIfManagedOrThrow
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// Proposal 1 of generic/dynamic API
// This seems like the most promising
@DynamicAPI
public inline operator fun <reified T> BaseRealmObject.get(propertyName: String): T {
    return get(propertyName, typeOf<T>())
}

@DynamicAPI
public operator fun <T> BaseRealmObject.set(name: String, value: T) {
    return this.runIfManagedOrThrow {
        RealmObjectHelper.setValueByName(this, name, value)
    }
}

@PublishedApi
internal fun <T> BaseRealmObject.get(propertyName: String, type: KType): T {
    return this.runIfManagedOrThrow {
        RealmObjectHelper.dynamicGetFromKType(
            obj = this,
            propertyName = propertyName,
            type = type
        )
    }
}

