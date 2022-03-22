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
import io.realm.internal.interop.PropertyInfo
import io.realm.internal.interop.NativePointer
import io.realm.internal.schema.ClassMetadata
import io.realm.internal.util.Validation
import io.realm.isValid
import kotlin.reflect.KClass

/**
 * Internal interface for Realm objects.
 *
 * The interface is added by the compiler plugin to all [RealmObject] classes to have an interface
 * exposing our internal API and compiler plugin additions without leaking it to the public
 * [RealmObject].
 */
// TODO Public due to being a transative dependency of Mediator
@Suppress("VariableNaming")
public interface RealmObjectInternal : RealmObject {
    public var `$realm$objectReference`: ObjectReference<out RealmObject>? // RealmObjectReference ?
}

internal fun RealmObject.asObjectReference(): ObjectReference<out RealmObject>? {
    return (this as RealmObjectInternal).`$realm$objectReference`
}

internal fun RealmObject.isManaged(): Boolean {
    return asObjectReference() != null
}

internal fun RealmObjectInternal.checkValid() {
    if (!this.isValid()) {
        throw IllegalStateException("Cannot perform this operation on an invalid/deleted object")
    }
}

internal fun RealmObjectInternal.propertyInfoOrThrow(
    propertyName: String
): PropertyInfo = asObjectReference()!!.propertyInfoOrThrow(propertyName)

internal fun RealmObjectInternal.getObjectPointer(): NativePointer? = asObjectReference()!!.`$realm$ObjectPointer`

internal fun RealmObjectInternal.getOwner(): RealmReference = asObjectReference()!!.`$realm$Owner`

internal fun RealmObjectInternal.getMediator(): Mediator = asObjectReference()!!.`$realm$Mediator`

internal fun RealmObjectInternal.getMetadata(): ClassMetadata = asObjectReference()!!.`$realm$metadata`

internal fun RealmObjectInternal.getClassName(): String = asObjectReference()!!.`$realm$ClassName`