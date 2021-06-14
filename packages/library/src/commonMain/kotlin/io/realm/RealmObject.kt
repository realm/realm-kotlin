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

import io.realm.internal.RealmObjectInternal
import io.realm.interop.RealmInterop

/**
 * Marker interface to define a model (managed by Realm).
 */
interface RealmObject

// FIXME API Currently just adding these as extension methods as putting them directly into
//  RealmModel would break compiler plugin. Reiterate along with
//  https://github.com/realm/realm-kotlin/issues/83
public fun RealmObject.delete() {
    MutableRealm.delete(this)
}

/**
 * Returns the Realm version of this object. This version number is tied to the transaction the object was read from.
 */
public var RealmObject.version: VersionId
    get() {
        val internalObject = this as RealmObjectInternal
        internalObject.`$realm$Pointer`?.let {
            // FIXME This check is required as realm_get_version_id does throw!? Core bug?
            if (RealmInterop.realm_is_closed(it)) {
                throw IllegalStateException("Cannot access properties on closed realm")
            }
            return VersionId(RealmInterop.realm_get_version_id(it))
        } ?: throw IllegalArgumentException("Cannot get version from an unmanaged object.")
    }
    private set(_) {
        throw UnsupportedOperationException("Setter is required by the Kotlin Compiler, but should not be called directly")
    }

/**
 * Returns whether or not this object is managed by Realm.
 *
 * Managed objects are only valid to use while the Realm is open, but also have access to all Realm API's like
 * queries or change listeners. Unmanaged objects behave like normal Kotlin objects and are completely seperate from
 * Realm.
 */
public fun RealmObject.isManaged(): Boolean {
    val internalObject = this as RealmObjectInternal
    return internalObject.`$realm$IsManaged`
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
