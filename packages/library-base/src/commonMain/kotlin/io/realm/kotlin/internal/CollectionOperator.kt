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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.interop.CapiT
import io.realm.kotlin.internal.interop.NativePointer

internal interface CollectionOperator<E, T> {
    val mediator: Mediator
    val realmReference: RealmReference
    val valueConverter: RealmValueConverter<E>
    val nativePointer: NativePointer<out CapiT>
}

internal enum class CollectionOperatorType {
    PRIMITIVE,
    REALM_ANY,
    REALM_OBJECT,
    EMBEDDED_OBJECT
}
