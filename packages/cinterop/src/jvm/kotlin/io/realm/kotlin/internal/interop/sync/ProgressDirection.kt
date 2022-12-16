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

package io.realm.kotlin.internal.interop.sync

import io.realm.kotlin.internal.interop.NativeEnumerated
import io.realm.kotlin.internal.interop.realm_sync_progress_direction_e

actual enum class ProgressDirection(override val nativeValue: Int) : NativeEnumerated {
    RLM_SYNC_PROGRESS_DIRECTION_UPLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_UPLOAD),
    RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD(realm_sync_progress_direction_e.RLM_SYNC_PROGRESS_DIRECTION_DOWNLOAD),
}
