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

package io.realm.internal

import io.realm.RealmObject
import io.realm.VersionId
import io.realm.isValid

/**
 * Internal interface for Realm objects.
 *
 * The interface is added by the compiler plugin to all [RealmObject] classes to have an interface
 * exposing our internal API and compiler plugin additions without leaking it to the public
 * [RealmObject].
 */
@Suppress("VariableNaming")
internal interface RealmObjectInternal : RealmObject, RealmLifeCycleHolder, io.realm.interop.RealmObjectInterop {
    // Names must match identifiers in compiler plugin (plugin-compiler/io.realm.compiler.Identifiers.kt)

    // Reference to the public Realm instance and internal transaction to which the object belongs.
    var `$realm$Owner`: RealmReference?
    var `$realm$TableName`: String?
    var `$realm$IsManaged`: Boolean
    var `$realm$Mediator`: Mediator?

    override fun realmLifeCycle(): RealmLifeCycle {
        return `$realm$Owner` ?: UnmanagedLifeCycle
    }

}

internal inline fun RealmObject.realmObjectInternal(): RealmObjectInternal {
    return this as RealmObjectInternal
}

internal fun RealmObjectInternal.checkValid() {
    if (!this.isValid()) {
        throw IllegalStateException("Cannot perform this operation on an invalid/deleted object")
    }
}
