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

package io.realm.kotlin.notifications.internal

import io.realm.kotlin.notifications.DeletedObject
import io.realm.kotlin.notifications.InitialObject
import io.realm.kotlin.notifications.PendingObject
import io.realm.kotlin.notifications.UpdatedObject
import io.realm.kotlin.types.BaseRealmObject

internal class InitialObjectImpl<O : BaseRealmObject>(override val obj: O) : InitialObject<O>

internal class UpdatedObjectImpl<O : BaseRealmObject>(
    override val obj: O,
    override val changedFields: Array<String>
) : UpdatedObject<O>

internal class DeletedObjectImpl<O : BaseRealmObject> : DeletedObject<O> {
    override val obj: O?
        get() = null
}

internal class PendingObjectImpl<O : BaseRealmObject> : PendingObject<O> {
    override val obj: O?
        get() = null
}
