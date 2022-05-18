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

import io.realm.BaseRealmObject
import io.realm.query.RealmSingleQuery

/**
 * This sealed class describe the possible events that can be observed on a [RealmSingleQuery] flow.
 *
 * It extends the sealed interface [ObjectChange] by adding the new event [PendingObject] on top of
 * its hierarchy. See [RealmSingleQuery.asFlow] for more information on how these events are emitted.
 *
 * Object event hierarchy diagram:
 * ```
 *                                   ┌───────────────────┐
 *                                   │ SingleQueryChange │
 *                                   └─────────┬─────────┘
 *                                ┌────────────┴───────────┐
 *                         ┌──────▼───────┐        ┌───────▼───────┐
 *                         │ ObjectChange │        │ PendingObject │
 *                         └──────┬───────┘        └───────────────┘
 *               ┌────────────────┼────────────────────┐
 *      ┌────────▼──────┐  ┌──────▼────────┐  ┌────────▼──────┐
 *      │ InitialObject │  │ UpdatedObject │  │ DeletedObject │
 *      └───────────────┘  └───────────────┘  └───────────────┘
 * ```
 */
public sealed interface SingleQueryChange<O : BaseRealmObject> {
    /**
     * Returns the newest state of object being observed. `null` is returned if there is no object to
     * observe.
     */
    public val obj: O?
}

/**
 * Describes the initial state where a query result does not contain any elements.
 */
public interface PendingObject<O : BaseRealmObject> : SingleQueryChange<O>

/**
 * This sealed interface describe the possible changes that can be observed to a Realm Object.
 *
 * The specific states are represented by the specific subclasses [InitialObject], [UpdatedObject] and
 * [DeletedObject].
 *
 * Changes can thus be consumed in a number of ways:
 *
 * ```
 * // Variant 1: Switch on the sealed interface
 * realm.filter<Person>().first().asFlow()
 *   .collect { objectChange: ObjectChange<Person> ->
 *       when(objectChange) {
 *          is InitialObject -> initPersonUI(objectChange.obj)
 *          is UpdatedObject -> updatePersonUi(objectChange.obj, objectChange.changedFields)
 *          is DeletedObject -> removePersonUi()
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the object
 * realm.filter<Person>().first().asFlow()
 *   .collect { objectChange: ObjectChange<Person> ->
 *       handleChange(objectChange.obj)
 *   }
 * ```
 *
 * For state of update changes, a list with the updated field names from the previous version is provided.
 */
public sealed interface ObjectChange<O : BaseRealmObject> : SingleQueryChange<O> {
    /**
     * Returns the newest state of object being observed. `null` is returned if the object
     * has been deleted.
     */
    override val obj: O?
}

/**
 * Initial event to be observed on a [RealmObject] or [EmbeddedObject] flow. It contains a
 * reference to the starting object state. Note, this state might be different than the object the
 * flow was registered on, if another thread or device updated the object in the meantime.
 */
public interface InitialObject<O : BaseRealmObject> : ObjectChange<O> {
    override val obj: O
}

/**
 * [RealmObject] or [EmbeddedObject] flow event that describes that an update has been performed on
 * to the observed object. It provides a reference to the object and a list of the changed field
 * names.
 */
public interface UpdatedObject<O : BaseRealmObject> : ObjectChange<O> {
    override val obj: O

    /**
     * Returns the names of properties that has changed.
     */
    public val changedFields: Array<String>

    /**
     * Checks if a given field has been changed.
     *
     * @param fieldName to be checked if its value has been changed.
     * @return `true` if the field has been changed. It returns `false` the field cannot be found
     * or the field hasn't been changed.
     */
    public fun isFieldChanged(fieldName: String): Boolean {
        return changedFields.firstOrNull { it == fieldName } != null
    }
}

/**
 * This interface describes the event is emitted deleted on a [RealmObject] or [EmbeddedObject]
 * flow. The flow will terminate after emitting this event.
 */
public interface DeletedObject<O : BaseRealmObject> : ObjectChange<O>
