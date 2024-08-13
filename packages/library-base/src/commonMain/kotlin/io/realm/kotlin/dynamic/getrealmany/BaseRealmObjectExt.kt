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

package io.realm.kotlin.dynamic.getrealmany

import io.realm.kotlin.internal.RealmObjectHelper
import io.realm.kotlin.internal.runIfManagedOrThrow
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmAny
import kotlin.reflect.typeOf

// Proposal 3 of generic/dynamic API
// Wrapping anything in RealmAny/Mixed to avoid the need of generics over. This would maybe be
// preferable for the non-schema properties of the "relaxed schema"-concept, but is really annoying
// to work with if you know the types as you would if using this as a general dynamic API for
// fixed schema properties. If you really want to work with RealmAny, you could just the
// `get<RealmAny>` variant from proposal 1.
public operator fun BaseRealmObject.get(propertyName: String): RealmAny {
    return this.runIfManagedOrThrow {
        RealmObjectHelper.dynamicGetFromKType(
            obj = this,
            propertyName = propertyName,
            type = typeOf<RealmAny>()
        )
    }
}

// FIXME Does this need to be typed
public operator fun <T> BaseRealmObject.set(name: String, value: T) {
    return this.runIfManagedOrThrow {
        RealmObjectHelper.setValueByName(this, name, value)
    }
}
