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

package io.realm.kotlin.internal.interop

import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic

actual object CoreErrorConverter {
    private val converter: AtomicRef<((CoreError) -> Throwable)?> = atomic(null)
    actual fun initialize(coreErrorConverter: (CoreError) -> Throwable) {
        converter.value = coreErrorConverter
    }

    fun convertCoreError(coreError: CoreError): Throwable =
        converter.value!!.invoke(coreError)
}
