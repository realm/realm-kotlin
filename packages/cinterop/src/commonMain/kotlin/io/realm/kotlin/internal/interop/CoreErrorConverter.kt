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

/**
 * This object allows the public API to control how exceptions are being surfaced from
 * `cinterop`.
 *
 * The public API should call [CoreErrorConverter.initialize] before using this class in order
 * to correctly map exceptions. If we fail to do that, the underlying exception will just
 * be thrown. This will leak implementation details, but is better than than crashing with a
 * `CoreErrorConverter has not been initialized`.
 */
expect object CoreErrorConverter {
    fun initialize(coreErrorConverter: (RealmCoreException) -> Throwable)
    fun convertCoreError(coreError: RealmCoreException): Throwable
}
