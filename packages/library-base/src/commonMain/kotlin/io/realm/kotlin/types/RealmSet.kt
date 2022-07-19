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

package io.realm.kotlin.types

import io.realm.kotlin.Deleteable
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.notifications.InitialSet
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.UpdatedSet
import kotlinx.coroutines.flow.Flow

/**
 * RealmSet is a collection that contains no duplicate elements.
 *
 * Similarly to [RealmList]s, a RealmSet can operate in `managed` and `unmanaged` modes. In
 * managed mode a RealmSet persists all its contents inside a realm whereas in unmanaged mode
 * it functions like a [MutableSet].
 *
 * Managed RealmSets can only be created by Realm and will automatically update their content
 * whenever the underlying Realm is updated. Managed RealmSets can only be accessed using the getter
 * that points to a RealmSet field of a [RealmObject].
 *
 * @param E the type of elements contained in the RealmSet.
 */
public interface RealmSet<E> : MutableSet<E>, Deleteable {

    /**
     * Observes changes to the RealmSet. The [Flow] will emit [InitialSet] once subscribed, and
     * then [UpdatedSet] on every change to the set. The flow will continue running indefinitely
     * until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the list.
     */
    public fun asFlow(): Flow<SetChange<E>>
}
