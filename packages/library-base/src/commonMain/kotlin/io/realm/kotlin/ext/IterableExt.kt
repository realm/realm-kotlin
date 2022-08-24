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

import io.realm.kotlin.internal.UnmanagedRealmList
import io.realm.kotlin.internal.UnmanagedRealmSet
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmSet

/**
 * Instantiates an **unmanaged** [RealmList] containing all the elements of this iterable.
 */
public fun <T> Iterable<T>.toRealmList(): RealmList<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedRealmList()
            1 -> realmListOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedRealmList<T>().apply { addAll(this@toRealmList) }
        }
    }
    return UnmanagedRealmList<T>().apply { addAll(this@toRealmList) }
}

/**
 * Instantiates an **unmanaged** [RealmSet] containing all the elements of this iterable.
 */
public fun <T> Iterable<T>.toRealmSet(): RealmSet<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedRealmSet()
            1 -> realmSetOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedRealmSet<T>().apply { addAll(this@toRealmSet) }
        }
    }
    return UnmanagedRealmSet<T>().apply { addAll(this@toRealmSet) }
}
