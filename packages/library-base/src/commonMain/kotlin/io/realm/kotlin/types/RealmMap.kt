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

/**
 * TODO
 */
// TODO flow and Deleteable
public interface RealmMap<K, V> : MutableMap<K, V>

/**
 * TODO
 */
public interface RealmDictionary<E> : RealmMap<String, E>

internal typealias RealmMapEntrySet<K, V> = MutableSet<MutableMap.MutableEntry<K, V>>

internal typealias RealmMapMutableEntry<K, V> = MutableMap.MutableEntry<K, V>

/**
 * Convenience alias for `MutableSet<MutableMap.MutableEntry<String, V>>`.
 *
 * The output produced by [RealmDictionary.entries] matches this alias and represents a
 * [RealmDictionary] in the form of a [MutableSet] of [RealmDictionaryMutableEntry] values.
 */
public typealias RealmDictionaryEntrySet<V> = RealmMapEntrySet<String, V>

/**
 * Convenience alias for `RealmMapMutableEntry<String, V>`. Represents the `String`-`V` pairs
 * contained by a [RealmDictionary].
 */
public typealias RealmDictionaryMutableEntry<V> = RealmMapMutableEntry<String, V>
