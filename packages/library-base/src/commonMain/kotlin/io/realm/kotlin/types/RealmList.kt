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

package io.realm.kotlin.types

import io.realm.kotlin.Deleteable
import io.realm.kotlin.MutableRealm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.dynamic.DynamicMutableRealm
import io.realm.kotlin.notifications.InitialList
import io.realm.kotlin.notifications.ListChange
import io.realm.kotlin.notifications.UpdatedList
import kotlinx.coroutines.flow.Flow

/**
 * RealmList is used to model one-to-many relationships in a [RealmObject] or [EmbeddedRealmObject].
 *
 * A RealmList has two modes: `managed` and `unmanaged`. In `managed` mode all objects are persisted
 * inside a Realm whereas in `unmanaged` mode it works as a normal [MutableList].
 *
 * Only Realm can create managed RealmLists. Managed RealmLists will automatically update their
 * content whenever the underlying Realm is updated. Said content can only be accessed using the
 * getter of a [RealmObject] or [EmbeddedRealmObject].
 *
 * Unmanaged RealmLists can be created by the user and can contain both managed and unmanaged
 * [RealmObject]s or [EmbeddedRealmObject]s. This is useful when dealing with JSON deserializers like
 * Gson or other frameworks that inject values into a class. Unmanaged elements in a list can be
 * added to a Realm using the [MutableRealm.copyToRealm] method.
 *
 * Deleting a list through [MutableRealm.delete] or [DynamicMutableRealm.delete] will delete any
 * referenced objects from the realm and clear the list.
 *
 * @param E the type of elements contained in the RealmList.
 */
public interface RealmList<E> : MutableList<E>, Deleteable {

    /**
     * Replaces the element at the specified position in this list with the specified element.
     *
     * @return the element previously at the specified position for list of primitives and
     * [RealmObject]s, but will return the newly imported object for lists of embedded objects,
     * as the previous element will be deleted as part of clearing its parent.
     *
     * @return the element previously at the specified position.
     */
    override fun set(index: Int, element: E): E

    /**
     * Observes changes to the RealmList. The [Flow] will emit an [InitialList] once subscribed, and
     * then an [UpdatedList] on every change to the list. The flow will continue running
     * indefinitely until canceled or until the parent object is deleted.
     *
     * The change calculations will run on the thread represented by
     * [RealmConfiguration.Builder.notificationDispatcher].
     *
     * @return a flow representing changes to the list.
     */
    public fun asFlow(): Flow<ListChange<E>>
}
