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
import io.realm.kotlin.internal.UnmanagedRealmList
import io.realm.kotlin.internal.asRealmList
import io.realm.kotlin.internal.getRealm
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.TypedRealmObject

/**
 * Instantiates an **unmanaged** [RealmList].
 */
public fun <T> realmListOf(vararg elements: T): RealmList<T> =
    if (elements.isNotEmpty()) elements.asRealmList() else UnmanagedRealmList()

/**
 * Makes an unmanaged in-memory copy of the elements in a managed [RealmList]. This is a deep copy
 * that will copy all referenced objects.
 *
 * @param depth limit of the deep copy. All object references after this depth will be `null`.
 * [RealmList]s and [RealmSet]s containing objects will be empty. Starting depth is 0.
 * @returns an in-memory copy of all input objects.
 * @throws IllegalArgumentException if depth < 0 or, or the list is not valid to copy.
 */
public inline fun <reified T : TypedRealmObject> RealmList<T>.copyFromRealm(depth: UInt = UInt.MAX_VALUE): List<T> {
    return this.getRealm<TypedRealm>()?.copyFromRealm(this, depth)
        ?: throw IllegalArgumentException("This RealmList is unmanaged. Only managed lists can be copied.")
}
