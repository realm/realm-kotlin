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

import io.realm.internal.MutableRealmImpl
import io.realm.internal.RealmObjectInternal
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.realmObjectInternal
import io.realm.notifications.ObjectChange
import kotlinx.coroutines.flow.Flow

/**
 * Marker interface to define a model (managed by Realm).
 */
interface RealmObject

/**
 * Returns whether the object is frozen or not.
 *
 * A frozen object is tied to a specific version of the data in the realm and fields retrieved
 * from this object instance will not update even if the object is updated in the Realm.
 *
 * @return true if the object is frozen, false otherwise.
 */
public fun RealmObject.isFrozen(): Boolean {
    return realmObjectInternal().isFrozen()
}

/**
 * Returns the Realm version of this object. This version number is tied to the transaction the object was read from.
 */
public fun RealmObject.version(): VersionId {
    return realmObjectInternal().version()
}

/**
 * Deletes the RealmObject.
 *
 * @throws InvalidArgumentException if invoked on an invalid object
 * @throws RuntimeException if invoked outside of a [Realm.write] or [Realm.writeBlocking] block.
 */
// FIXME API Currently just adding these as extension methods as putting them directly into
//  RealmModel would break compiler plugin. Reiterate along with
//  https://github.com/realm/realm-kotlin/issues/83
fun RealmObject.delete() {
    MutableRealmImpl.delete(this)
}

/**
 * Returns whether or not this object is managed by Realm.
 *
 * Managed objects are only valid to use while the Realm is open, but also have access to all Realm API's like
 * queries or change listeners. Unmanaged objects behave like normal Kotlin objects and are completely seperate from
 * Realm.
 */
fun RealmObject.isManaged(): Boolean {
    return realmObjectInternal().`$realm$IsManaged`
}

/**
 * Checks whether or not two Realm objects represent the same underlying object, by advancing the
 * [behind] object up to the [front] Realm version.
 */
internal fun equalsToResolvingIn(front: RealmObjectInternal, behind: RealmObjectInternal): Boolean {
    val dbPointer: NativePointer = front.`$realm$Owner`!!.dbPointer
    val resolvedPointer: NativePointer =
        RealmInterop.realm_object_resolve_in(behind.`$realm$ObjectPointer`!!, dbPointer)
            ?: return false

    return RealmInterop.realm_equals(front.`$realm$ObjectPointer`!!, resolvedPointer)
}

/**
 * Checks whether [this] and [other] represent the same underlying object or not.
 */
internal fun RealmObject.equalsTo(other: RealmObject?): Boolean {
    if (other == null) return false
    if (other !is RealmObjectInternal) return false

    if (!isManaged() || !other.isManaged()) {
        throw IllegalStateException("Cannot compare unmanaged objects.")
    }

    val thisInternal = this.realmObjectInternal()
    val otherInternal = other.realmObjectInternal()

    val versionId = thisInternal.`$realm$Owner`!!.version()
    val otherVersionId = otherInternal.`$realm$Owner`!!.version()

    return if (versionId > otherVersionId) {
        equalsToResolvingIn(thisInternal, otherInternal)
    } else if (versionId < otherVersionId) {
        equalsToResolvingIn(otherInternal, thisInternal)
    } else {
        RealmInterop.realm_equals(thisInternal.`$realm$ObjectPointer`!!, otherInternal.`$realm$ObjectPointer`!!)
    }
}

/**
 * Returns true if this object is still valid to use, i.e. the Realm is open and the underlying object has
 * not been deleted. Unmanaged objects are always valid.
 */
public fun RealmObject.isValid(): Boolean {
    return if (isManaged()) {
        val internalObject = this as RealmObjectInternal
        val ptr = internalObject.`$realm$ObjectPointer`
        return if (ptr != null) {
            RealmInterop.realm_object_is_valid(ptr)
        } else {
            false
        }
    } else {
        // Unmanaged objects are always valid
        true
    }
}

/**
 * Observe changes to a Realm object. The flow would emit an [ObjectChange] once subscribed and
 * then, on every change to the object. If the observed object is deleted from the Realm, the flow
 * will complete, otherwise it will continue running until canceled.
 *
 * The change calculations will on on the thread represented by [Configuration.notificationDispatcher].
 *
 * @return a flow representing changes to the object.
 */
public fun <T : RealmObject, C : ObjectChange<T>> T.asFlow(): Flow<ObjectChange<T>> {
    checkNotificationsAvailable()
    val internalObject = this as RealmObjectInternal
    @Suppress("UNCHECKED_CAST")
    return (internalObject.`$realm$Owner`!!).owner.registerObserver(this) as Flow<ObjectChange<T>>
}

private fun RealmObject.checkNotificationsAvailable() {
    val internalObject = this as RealmObjectInternal
    val realm = internalObject.`$realm$Owner`
    if (!isManaged()) {
        throw IllegalStateException("Changes cannot be observed on unmanaged objects.")
    }
    if (realm != null && RealmInterop.realm_is_closed(realm.dbPointer)) {
        throw IllegalStateException("Changes cannot be observed when the Realm has been closed.")
    }
    if (!isValid()) {
        throw IllegalStateException("Changes cannot be observed on objects that have been deleted from the Realm.")
    }
}
