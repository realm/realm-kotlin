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

package io.realm.kotlin.notifications

import io.realm.kotlin.types.RealmSet

/**
 * This sealed interface describes the possible changes that can happen to a [RealmSet] collection.
 *
 * The states are represented by the specific subclasses [InitialSet], [UpdatedSet] and
 * [DeletedSet]. When the set is deleted an empty set is emitted instead of `null`.
 *
 * Since sets do not expose indices your UI components will have to manually handle updates:
 *
 * ```
 * person.addresses.asFlow()
 *   .collect { setChange: SetChange<Address> ->
 *       handleChange(setChange.set)
 *   }
 * ```
 */
public sealed interface SetChange<T> {
    public val set: RealmSet<T>
}

/**
 * Initial event to be observed on a [RealmSet] flow. It contains a reference to the starting set
 * state. Note, this state might be different than the set the flow was registered on, if another
 * thread or device updated the object in the meantime.
 */
public interface InitialSet<T> : SetChange<T>

/**
 * [RealmSet] flow event that describes that an update has been performed on the observed set. It
 * provides a reference to the updated set and a number of properties that describe the changes
 * performed on the set.
 */
public interface UpdatedSet<T> : SetChange<T>, SetChangeSet

/**
 * This event is emitted when the parent object owning the set has been deleted, which in turn also
 * removes the set. The flow will terminate after observing this event.
 */
public interface DeletedSet<T> : SetChange<T>
