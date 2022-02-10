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

sealed interface ObjectChange<O : RealmObject> {
    /**
     * Returns the newest state of object being observed. `null` is returned if the object
     * has been deleted.
     */
    val obj: O?
}
interface InitialObject<O : RealmObject> : ObjectChange<O> {
    override val obj: O
}

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
interface DeletedObject<O : RealmObject> : ObjectChange<O>
