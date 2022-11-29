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
import io.realm.kotlin.internal.UnmanagedRealmSet
import io.realm.kotlin.internal.asRealmSet
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmObject
import io.realm.kotlin.types.RealmSet

/**
 * Instantiates an **unmanaged** [RealmSet].
 */
public fun <T> realmSetOf(vararg elements: T): RealmSet<T> =
    if (elements.isNotEmpty()) elements.asRealmSet() else UnmanagedRealmSet()

/**
 * Makes an unmanaged in-memory copy of the elements in a managed [RealmSet]. This is a deep copy
 * that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <T : RealmObject> RealmSet<T>.copyFromRealm(depth: UInt = UInt.MAX_VALUE): Set<T> {
    return this.getRealm<TypedRealm>()?.let { realm ->
        realm.copyFromRealm(this, depth).toSet()
    } ?: throw IllegalArgumentException("This RealmSet is unmanaged. Only managed sets can be copied.")
}
