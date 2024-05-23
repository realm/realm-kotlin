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

import io.realm.kotlin.MutableRealm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.ext.realmDictionaryOf
import io.realm.kotlin.internal.RealmMapEntrySet
import io.realm.kotlin.internal.RealmMapMutableEntry
import io.realm.kotlin.notifications.InitialMap
import io.realm.kotlin.notifications.MapChange
import io.realm.kotlin.notifications.UpdatedMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * A `RealmMap` is used to map keys to values. `RealmMap`s cannot contain duplicate keys and each
 * key can be mapped to at most one value. `RealmMap`s cannot have `null` keys but can have `null`
 * values.
 *
 * Similarly to [RealmList] and [RealmSet], `RealmDictionary` properties cannot be nullable.
 *
 * Most importantly, **`RealmMap`s can only have `String` keys and should not be used to define
 * properties in [RealmObject]s.** If you need to use a `Map<String, V>` or a dictionary-type data
 * structure for your model you should use [RealmDictionary].
 *
 * @param K the type of the keys stored in this map
 * @param V the type of the values stored in this map
 */
public interface RealmMap<K, V> : MutableMap<K, V> {
    /**
     * Observes changes to the `RealmMap`. The [Flow] will emit [InitialMap] once subscribed,
     * and then [UpdatedMap] on every change to the dictionary. The flow will continue
     * running indefinitely until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @param keyPaths An optional list of model class properties that defines when a change to
     * objects inside the map will result in a change being emitted. For maps, keypaths are
     * evaluated based on the values of the map. This means that keypaths are only supported
     * for maps containing realm objects. Nested properties can be defined using a dotted syntax,
     * e.g. `parent.child.name`. Wildcards `*` can be be used to capture all properties at a given
     * level, e.g. `child.*` or `*.*`. If no keypaths are provided, changes to all top-level
     * properties and nested properties up to 4 levels down will trigger a change
     * @return a flow representing changes to the dictionary.
     * @throws IllegalArgumentException if keypaths are invalid or the map does not contain realm
     * objects.
     * @throws CancellationException if the stream produces changes faster than the consumer can
     * consume them and results in a buffer overflow.
     */
    public fun asFlow(keyPaths: List<String>? = null): Flow<MapChange<K, V>>
}

/**
 * A `RealmDictionary` is a specialization for [RealmMap]s whose keys are `Strings`.
 *
 * Similarly to [RealmList] or [RealmSet], `RealmMap` can operate in managed and unmanaged modes. In
 * managed mode a `RealmDictionary` persists all its contents in a Realm instance whereas unmanaged
 * dictionaries are backed by an in-memory [LinkedHashMap].
 *
 * A managed dictionary can only be created by Realm and will automatically update its content
 * whenever its underlying realm is updated. Managed dictionaries can only be accessed using the
 * getter that points to a `RealmDictionary` property of a managed [RealmObject].
 *
 * Unmanaged dictionaries can be created by calling [realmDictionaryOf] and may contain both managed
 * and unmanaged [RealmObject]s. Unmanaged dictionaries can be added to a realm using the
 * [MutableRealm.copyToRealm] function with an object containing an unmanaged dictionary.
 *
 * A `RealmDictionary` may contain any type of Realm primitive nullable and non-nullable values.
 * [RealmObject]s and [EmbeddedRealmObject]s are also supported but **must be declared nullable.**
 *
 * @param V the type of the values stored in this map
 */
public interface RealmDictionary<V> : RealmMap<String, V>

/**
 * Convenience alias for `MutableSet<MutableMap.MutableEntry<String, V>>`.
 *
 * The output produced by [RealmDictionary.entries] matches this alias and represents a
 * [RealmDictionary] in the form of a [MutableSet] of [RealmDictionaryMutableEntry] values.
 */
public typealias RealmDictionaryEntrySet<V> = RealmMapEntrySet<String, V>

/**
 * Convenience alias for `RealmMapMutableEntry<String, V>`. Represents the `String`-`V` pairs
 * stored by a [RealmDictionary].
 */
public typealias RealmDictionaryMutableEntry<V> = RealmMapMutableEntry<String, V>
