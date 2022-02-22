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
package io.realm.notifications

import io.realm.RealmObject
import io.realm.RealmResults

/**
 * This sealed interface describe the possible changes that can happen to a query results collection.
 *
 * The states are represented by the specific subclasses [InitialResults] and [UpdatedResults].
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * realm.query<Person>().asFlow()
 *   .collect { it: ResultsChange<Person> ->
 *       when(result) {
 *          is InitialList -> setUIList(it.list)
 *          is UpdatedList -> updateUIList(it) // Android RecyclerView knows how to animate ranges
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the list
 * realm.query<Person>().asFlow()
 *   .collect { it: ResultsChange<Person> ->
 *       handleChange(it.list)
 *   }
 * ```
 *
 * When the list is updated, extra information is provided describing the changes from the previous
 * version. This information is formatted in a way that can be feed directly to drive animations on UI
 * components like `RecyclerView`. In order to access this information, the [ListChange] must be cast
 * to the appropriate subclass.
 *
 * ```
 * realm.query<Person>().asFlow()
 *   .collect { it: ListChange<Person> ->
 *       when(result) {
 *          is InitialList -> setList(it.list)
 *          is UpdatedList -> { // Automatic cast to UpdatedList
 *              updateList(
 *                  it.list,
 *                  it.deletionRanges,
 *                  it.insertionRanges,
 *                  it.changeRanges
 *             )
 *          }
 *          is DeletedList -> deleteList(it.list)
 *       }
 *   }
 * ```
 */
sealed interface ResultsChange<T : RealmObject> {
    val list: RealmResults<T>
}

/**
 * Initial event to be emitted on a [RealmResults] flow. It contains a reference to the
 * starting query results state.
 */
interface InitialResults<T : RealmObject> : ResultsChange<T>

/**
 * [RealmResults] flow event that describes that an update has been performed on to the
 * observed list. It provides a reference to the list and a set of properties that describes the changes
 * performed on the list.
 */
interface UpdatedResults<T : RealmObject> : ResultsChange<T>, CollectionChangeSet
