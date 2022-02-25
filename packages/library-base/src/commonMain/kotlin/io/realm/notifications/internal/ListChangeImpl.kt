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

package io.realm.notifications.internal

import io.realm.RealmList
import io.realm.notifications.DeletedList
import io.realm.notifications.InitialList
import io.realm.notifications.UpdatedList

internal class InitialListImpl<T>(override val list: RealmList<T>) : InitialList<T>

internal class UpdatedListImpl<T>(
    override val list: RealmList<T>,
    collectionChangeSet: CollectionChangeSet
) : UpdatedList<T>, CollectionChangeSet by collectionChangeSet

internal class DeletedListImpl<T>(override val list: RealmList<T>) : DeletedList<T>
