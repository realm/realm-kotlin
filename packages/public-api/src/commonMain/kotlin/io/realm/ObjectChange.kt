package io.realm

import io.realm.base.BaseRealmModel

// Wraps both the object and the change data as Flows only support observing one class
// This makes changelisteners and flows have the same API.
class ObjectChange<E>(val obj: E?, val changedFields: Array<String>) {
    fun isFieldChanged(fieldName: String): Boolean { TODO() }
}
