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

import io.realm.kotlin.internal.UnmanagedRealmDictionary
import io.realm.kotlin.internal.UnmanagedRealmList
import io.realm.kotlin.internal.UnmanagedRealmSet
import io.realm.kotlin.types.RealmDictionary
import io.realm.kotlin.types.RealmList
import io.realm.kotlin.types.RealmDictionaryEntrySet
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

/**
 * Instantiates an **unmanaged** [RealmDictionary] containing all the elements of this iterable of
 * [Pair]s of [String]s and [T]s.
 */
public fun <T> Iterable<Pair<String, T>>.toRealmDictionary(): RealmDictionary<T> {
    if (this is Collection) {
        return when (size) {
            0 -> UnmanagedRealmDictionary()
            1 -> realmDictionaryOf(if (this is List) get(0) else iterator().next())
            else -> UnmanagedRealmDictionary<T>().apply {
                this.putAll(this@toRealmDictionary)
            }
        }
    }
    return UnmanagedRealmDictionary<T>().apply {
        this.putAll(this@toRealmDictionary)
    }
}

/**
 * Instantiates an **unmanaged** [RealmDictionary] containing all the elements of the receiver
 * [RealmDictionaryEntrySet].
 */
public fun <T> RealmDictionaryEntrySet<T>.toRealmDictionary(): RealmDictionary<T> {
    return when (size) {
        0 -> UnmanagedRealmDictionary()
        else -> map { Pair(it.key, it.value) }
            .toRealmDictionary()
    }
}
