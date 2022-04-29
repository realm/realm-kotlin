/*
 * Copyright 2020 Realm Inc.
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

package io.realm

import io.realm.internal.UnmanagedState
import io.realm.internal.checkNotificationsAvailable
import io.realm.internal.interop.RealmInterop
import io.realm.internal.realmObjectReference
import io.realm.internal.runIfManaged
import io.realm.migration.AutomaticSchemaMigration
import io.realm.notifications.DeletedObject
import io.realm.notifications.InitialObject
import io.realm.notifications.ObjectChange
import io.realm.notifications.UpdatedObject
import kotlinx.coroutines.flow.Flow

/**
 * Marker interface to define a model (managed by Realm).
 */

public interface BaseRealmObject : Deleteable
public interface RealmObject : BaseRealmObject

public interface EmbeddedObject : BaseRealmObject {
// TODO Consider adding
//  public val parent: BaseRealmObject
}


/**
 * Returns whether the object is frozen or not.
 *
 * A frozen object is tied to a specific version of the data in the realm and fields retrieved
 * from this object instance will not update even if the object is updated in the Realm.
 *
 * @return true if the object is frozen, false otherwise.
 */
public fun BaseRealmObject.isFrozen(): Boolean =
    (realmObjectReference ?: UnmanagedState).isFrozen()

/**
 * Returns the Realm version of this object. This version number is tied to the transaction the object was read from.
 */
public fun BaseRealmObject.version(): VersionId =
    (realmObjectReference ?: UnmanagedState).version()

/**
 * Returns whether or not this object is managed by Realm.
 *
 * Managed objects are only valid to use while the Realm is open, but also have access to all Realm API's like
 * queries or change listeners. Unmanaged objects behave like normal Kotlin objects and are completely separate from
 * Realm.
 */
public fun BaseRealmObject.isManaged(): Boolean = realmObjectReference != null

/**
 * Returns true if this object is still valid to use, i.e. the Realm is open and the underlying object has
 * not been deleted. Unmanaged objects are always valid.
 */
public fun BaseRealmObject.isValid(): Boolean = runIfManaged {
    return RealmInterop.realm_object_is_valid(objectPointer)
} ?: true

/**
 * Observe changes to a Realm object. The flow would emit an [InitialObject] once subscribed and
 * then, on every change to the object an [UpdatedObject]. If the observed object is deleted from
 * the Realm, the flow would emit a [DeletedObject] and then will complete, otherwise it will continue
 * running until canceled.
 *
 * The change calculations will on on the thread represented by [Configuration.notificationDispatcher].
 *
 * @return a flow representing changes to the object.
 * @throws UnsupportedOperationException if called on a live [BaseRealmObject] from a write transaction
 * ([Realm.write]) or on a [DynamicRealmObject] inside a migration
 * ([AutomaticSchemaMigration.migrate]).
 */
public fun <T : BaseRealmObject> T.asFlow(): Flow<ObjectChange<T>> = runIfManaged {
    checkNotificationsAvailable()
    return owner.owner.registerObserver(this) as Flow<ObjectChange<T>>
} ?: throw IllegalStateException("Changes cannot be observed on unmanaged objects.")
