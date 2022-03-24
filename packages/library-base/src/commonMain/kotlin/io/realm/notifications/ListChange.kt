/*
 * Copyright 2021 Realm Inc.
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

package io.realm.notifications

import io.realm.RealmList

/**
 * This sealed interface describes the possible changes that can happen to a [RealmList] collection.
 *
 * The states are represented by the specific subclasses [InitialList], [UpdatedList] and
 * [DeletedList]. When the list is deleted an empty list is emitted instead of `null`.
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * person.addresses.asFlow()
 *   .collect { listChange: ListChange<Address> ->
 *       when(listChange) {
 *          is InitialList -> setAddressesUIList(listChange.list)
 *          is UpdatedList -> updateAddressesUIList(listChange) // Android RecyclerView knows how to animate ranges
 *          is DeletedList -> deleteAddressesUIList()
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the list
 * person.addresses.asFlow()
 *   .collect { listChange: ListChange<Address> ->
 *       handleChange(listChange.list)
 *   }
 * ```
 *
 * When the list is updated, extra information is provided describing the changes from the previous
 * version. This information is formatted in a way that can be feed directly to drive animations on UI
 * components like `RecyclerView`. In order to access this information, the [ListChange] must be cast
 * to the appropriate subclass.
 *
 * ```
 * person.addresses.asFlow()
 *   .collect { listChange: ListChange<Address> ->
 *       when(listChange) {
 *          is InitialList -> setList(listChange.list)
 *          is UpdatedList -> { // Automatic cast to UpdatedList
 *              updateList(
 *                  listChange.list,
 *                  listChange.deletionRanges,
 *                  listChange.insertionRanges,
 *                  listChange.changeRanges
 *             )
 *          }
 *          is DeletedList -> deleteList()
 *       }
 *   }
 * ```
 */
public sealed interface ListChange<T> {
    public val list: RealmList<T>
}

/**
 * Initial event to be observed on a [RealmList] flow. It contains a reference to the starting list
 * state. Note, this state might be different than the list the flow was registered on, if another
 * thread or device updated the object in the meantime.
 */
public interface InitialList<T> : ListChange<T>

/**
 * [RealmList] flow event that describes that an update has been performed on the observed list. It
 * provides a reference to the updated list and a set of properties that describes the changes
 * performed on the list.
 */
public interface UpdatedList<T> : ListChange<T>, ListChangeSet

/**
 * This event is emitted when the parent object owning the list has been deleted, which in turn also
 * removes the list. The flow will terminate after observing this event.
 */
public interface DeletedList<T> : ListChange<T>
