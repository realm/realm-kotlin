package io.realm.notifications

import io.realm.RealmObject

sealed interface ObjectChange<O : RealmObject> {
    /**
     * Returns the newest state of object being observed. `null` is returned if the object
     * has been deleted.
     */
    val obj: O?
}
interface InitialObject<O : RealmObject> : ObjectChange<O>

interface UpdatedObject<O : RealmObject> : ObjectChange<O> {
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
