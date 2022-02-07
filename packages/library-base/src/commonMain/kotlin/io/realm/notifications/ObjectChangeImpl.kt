package io.realm.notifications

import io.realm.RealmObject

internal class InitialObjectImpl<O : RealmObject>(override val obj: O) : InitialObject<O>

internal class UpdatedObjectImpl<O : RealmObject>(
    override val obj: O,
    override val changedFields: Array<String>
) : UpdatedObject<O>

internal class DeletedObjectImpl<O : RealmObject> : DeletedObject<O> {
    override val obj: O?
        get() = null
}
