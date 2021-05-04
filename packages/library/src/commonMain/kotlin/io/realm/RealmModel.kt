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

package io.realm

import io.realm.internal.RealmModelInternal
import io.realm.interop.RealmInterop

// FIXME API Currently just adding these as extension methods as putting them directly into
//  RealmModel would break compiler plugin. Reiterate along with
//  https://github.com/realm/realm-kotlin/issues/83

fun RealmObject.delete() {
    MutableRealm.delete(this)
}

var RealmObject.version: VersionId
    get() {
        val internalObject = this as RealmModelInternal
        internalObject.`$realm$Pointer`?.let {
            val (version, index) = RealmInterop.realm_get_version_id(it)
            return VersionId(version, index)
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
fun RealmObject.isManaged(): Boolean {
    val internalObject = this as RealmModelInternal
    return internalObject.`$realm$IsManaged`
}
