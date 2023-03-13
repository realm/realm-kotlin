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

package io.realm.kotlin.notifications

import io.realm.kotlin.types.RealmMap

/**
 * This sealed interface describes the possible changes that can happen to a [RealmMap].
 *
 * The states are represented by the specific subclasses [InitialMap], [UpdatedMap] and
 * [DeletedMap]. When the map is deleted an empty map is emitted instead of `null`.
 *
 * Since maps do not expose indices your UI components will have to manually handle updates:
 *
 * ```
 * person.addresses.asFlow()
 *   .collect { mapChange: MapChange<String, Address> ->
 *       handleChange(mapChange.map)
 *   }
 * ```
 */
public sealed interface MapChange<K, V> {
    public val map: RealmMap<K, V>
}

/**
 * Initial event to be observed on a [RealmMap] flow. It contains a reference to the starting map
 * state. Note, this state might be different than the map the flow was registered on, if another
 * thread or device updated the object in the meantime.
 */
public interface InitialMap<K, V> : MapChange<K, V>

/**
 * [RealmMap] flow event that describes that an update has been performed on the observed map. It
 * provides a reference to the updated map and a number of properties that describe the changes
 * performed on the map.
 */
public interface UpdatedMap<K, V> : MapChange<K, V>, MapChangeSet<K>

/**
 * This event is emitted when the parent object owning the map has been deleted, which in turn also
 * removes the map. The flow will terminate after observing this event.
 */
public interface DeletedMap<K, V> : MapChange<K, V>
