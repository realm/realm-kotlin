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

package io.realm.kotlin.dynamic.getinterface

import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.runIfManagedOrThrow
import io.realm.kotlin.types.BaseRealmObject
import kotlin.reflect.KType
import kotlin.reflect.typeOf

// Proposal 2 of generic/dynamic API
// Hiding things a bit by only exposing it on a specific type. This is a bit cumbersome as you
// cannot use a single entry point to get into the dynamic domain (requires to use .relaxed
// everywhere)
public interface RelaxedRealmObject

// FIXME Naming
//  - Extras?
public val BaseRealmObject.relaxed: RelaxedRealmObject
    get() = this as RelaxedRealmObject


public inline operator fun <reified T> RelaxedRealmObject.get(propertyName: String): T {
    return get(propertyName, typeOf<T>())
}

public operator fun <T> RelaxedRealmObject.set(name: String, value: T) {
    return (this as BaseRealmObject).runIfManagedOrThrow {
        RealmObjectHelper.setValueByName(this, name, value)
    }
}

@PublishedApi
internal fun <T> RelaxedRealmObject.get(propertyName: String, type: KType): T {
    return (this as BaseRealmObject).runIfManagedOrThrow {
        RealmObjectHelper.dynamicGetFromKType(
            obj = this,
            propertyName = propertyName,
            type = type
        )
    }
}
