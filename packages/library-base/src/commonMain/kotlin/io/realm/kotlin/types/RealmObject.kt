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

package io.realm.kotlin.types

import io.realm.kotlin.internal.Mediator
import io.realm.kotlin.internal.RealmObjectInternal
import io.realm.kotlin.internal.RealmObjectReference
import io.realm.kotlin.internal.RealmReference
import io.realm.kotlin.internal.interop.RealmInterop

/**
 * Marker interface to define a model (managed by Realm).
 */
public interface RealmObject : BaseRealmObject {
    /**
     * TODO
     * Apparently we can use extension functions for this, which will bring the correct type
     */
    public fun <T> copyFromRealm(depth: Int = Int.MAX_VALUE, closeAfterCopy: Boolean = true): T {
        if (this is RealmObjectInternal) {
            val objectRef: RealmObjectReference<out BaseRealmObject> = this.io_realm_kotlin_objectReference!!
            val realmRef: RealmReference = objectRef.owner
            val mediator: Mediator = realmRef.owner.configuration.mediator
            val copy = io.realm.kotlin.internal.createDetachedCopy(mediator, realmRef, this, depth)
            if (closeAfterCopy) {
                RealmInterop.realm_release(objectRef.objectPointer)
            }
            return copy as T
        } else {
            throw IllegalStateException("Object has not been modified by the Realm Compiler " +
                    "Plugin. Has the Realm Gradle Plugin been applied to the project with this " +
                    "model class?")
        }
    }
}
