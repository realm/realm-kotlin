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

import io.realm.RealmObject

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
 *   .collect { it: ObjectChange<Person> ->
 *       when(result) {
 *          is InitialObject -> initPersonUI(it.obj)
 *          is UpdatedObject -> updatePersonUi(it.obj, it.changedFields)
 *          is DeletedObject -> removePersonUi()
 *       }
 *   }
 *
 *
 * // Variant 2: Just pass on the object
 * realm.filter<Person>().first().asFlow()
 *   .collect { it: ObjectChange<Person> ->
 *       handleChange(it.obj)
 *   }
 * ```
 *
 * For state of update changes, a list with the updated field names from the previous version is provided.
 */
sealed interface ObjectChange<O : RealmObject> {
    /**
     * Returns the newest state of object being observed. `null` is returned if the object
     * has been deleted.
     */
    val obj: O?
}

/**
 * Initial event to be observed on a RealmObject flow. It contains a reference to the starting object
 * state. Note, this state might be different than the object the flow was registered on, if another thread or device updated the object in the meantime.
 */
interface InitialObject<O : RealmObject> : ObjectChange<O> {
    override val obj: O
}

/**
 * Realm object flow event that describes that an update has been performed on to the observed object.
 * It provides a reference to the object and a list of the changed field names.
 */
interface UpdatedObject<O : RealmObject> : ObjectChange<O> {
    override val obj: O

    /**
     * Returns the names of properties that has changed.
     */
    val changedFields: Array<String>

    /**
     * Checks if a given field has been changed.
     *
     * @param fieldName to be checked if its value has been changed.
     * @return `true` if the field has been changed. It returns `false` the field cannot be found
     * or the field hasn't been changed.
     */
    fun isFieldChanged(fieldName: String): Boolean {
        return changedFields.firstOrNull { it == fieldName } != null
    }
}

/**
 * This interface describes the event where an observed object is deleted. The flow will terminate
 * after emitting this event.
 */
interface DeletedObject<O : RealmObject> : ObjectChange<O>
