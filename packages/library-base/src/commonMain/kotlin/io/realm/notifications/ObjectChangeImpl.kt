package io.realm.notifications

import io.realm.RealmObject

internal class InitialObjectImpl<O : RealmObject>(override val obj: O) : InitialObject<O> {
    override val state: ObjectChange.State
        get() = ObjectChange.State.INITIAL
}

internal class UpdatedObjectImpl<O : RealmObject>(
    override val obj: O,
    override val changedFields: Array<String>
) : UpdatedObject<O> {
    override val state: ObjectChange.State
        get() = ObjectChange.State.UPDATED
}

internal class DeletedObjectImpl<O : RealmObject> : DeletedObject<O> {
    override val state: ObjectChange.State
        get() = ObjectChange.State.DELETED

    override val obj: O?
        get() = null
}
