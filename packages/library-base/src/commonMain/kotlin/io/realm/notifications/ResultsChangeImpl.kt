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
import io.realm.RealmResults

internal class InitialResultsImpl<T : RealmObject>(
    override val list: RealmResults<T>
) : InitialResults<T>

internal class UpdatedResultsImpl<T : RealmObject>(
    override val list: RealmResults<T>,
    listChangeSet: ListChangeSet
) : UpdatedResults<T>, ListChangeSet by listChangeSet
