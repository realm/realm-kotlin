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

internal class InitialObjectImpl<O : RealmObject>(override val obj: O) : InitialObject<O>

internal class UpdatedObjectImpl<O : RealmObject>(
    override val obj: O,
    override val changedFields: Array<String>
) : UpdatedObject<O>

internal class DeletedObjectImpl<O : RealmObject> : DeletedObject<O> {
    override val obj: O?
        get() = null
}

internal class PendingObjectImpl<O : RealmObject> : PendingObject<O> {
    override val obj: O?
        get() = null
}
