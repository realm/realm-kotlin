/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.types

public interface RealmMap<K, V> : MutableMap<K, V>

public interface RealmDictionary<E> : RealmMap<String, E>

internal class UnmanagedRealmDictionary<E> : RealmDictionary<E>, MutableMap<String, E> by mutableMapOf()

internal class ManagedRealmDictionary<E> : AbstractMutableMap<String, E>(), RealmDictionary<E> {
    override val entries: MutableSet<MutableMap.MutableEntry<String, E>>
        get() = TODO("Not yet implemented")

    override fun put(key: String, value: E): E? {
        TODO("Not yet implemented")
    }
}
