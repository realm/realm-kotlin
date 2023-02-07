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

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.internal.RealmMapEntrySet
import io.realm.kotlin.internal.RealmMapMutableEntry
import io.realm.kotlin.notifications.DictionaryChange
import io.realm.kotlin.notifications.InitialDictionary
import io.realm.kotlin.notifications.InitialMap
import io.realm.kotlin.notifications.MapChange
import io.realm.kotlin.notifications.UpdatedDictionary
import io.realm.kotlin.notifications.UpdatedMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow

/**
 * TODO
 */
public interface RealmMap<K, V> : MutableMap<K, V>

/**
 * TODO
 */
public interface RealmDictionary<E> : RealmMap<String, E> {
    /**
     * Observes changes to the RealmDictionary. The [Flow] will emit [InitialDictionary] once
     * subscribed, and then [UpdatedDictionary] on every change to the dictionary. The flow will
     * continue running indefinitely until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * The flow has an internal buffer of [Channel.BUFFERED] but if the consumer fails to consume
     * the elements in a timely manner the coroutine scope will be cancelled with a
     * [CancellationException].
     *
     * @return a flow representing changes to the dictionary.
     */
    public fun asFlow(): Flow<DictionaryChange<E>>
}

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
