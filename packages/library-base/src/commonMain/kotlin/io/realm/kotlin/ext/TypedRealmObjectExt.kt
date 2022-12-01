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

import io.realm.kotlin.TypedRealm
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.TypedRealmObject

/**
 * Makes an unmanaged in-memory copy of an already persisted [io.realm.kotlin.types.RealmObject].
 * This is a deep copy that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @returns an in-memory copy of the input object.
 * @throws IllegalArgumentException if the object is not a valid object to copy.
 */
public inline fun <reified T : TypedRealmObject> T.copyFromRealm(depth: UInt = UInt.MAX_VALUE): T {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth)
    } ?: throw IllegalArgumentException("This object is unmanaged. Only managed objects can be copied.")
}
